package com.hedera.mirror.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.amazonaws.*;
import com.amazonaws.auth.*;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import com.google.common.base.Stopwatch;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.domain.NodeAddress;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.utilities.Utility;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class Downloader {
	protected final Logger log = LogManager.getLogger(getClass());

	private final TransferManager transferManager;
	private final Comparator<String> s3KeyComparator;
	protected final Comparator<String> fileNameComparator;
	protected final Comparator<Path> pathComparator;
	protected List<String> nodeAccountIds;
	protected final ApplicationStatusRepository applicationStatusRepository;
    protected final NetworkAddressBook networkAddressBook;
	protected final DownloaderProperties downloaderProperties;
	private final ExecutorService signatureDownloadThreadPool; // Thread pool used one per node during the download process for signatures.

	public enum DownloadType {RCD, BALANCE, EVENT};

	public Downloader(TransferManager transferManager, ApplicationStatusRepository applicationStatusRepository, NetworkAddressBook networkAddressBook, DownloaderProperties downloaderProperties) {
	    this.transferManager = transferManager;
		this.applicationStatusRepository = applicationStatusRepository;
		this.networkAddressBook = networkAddressBook;
		this.downloaderProperties = downloaderProperties;
		signatureDownloadThreadPool = Executors.newFixedThreadPool(downloaderProperties.getThreads());
		nodeAccountIds = networkAddressBook.load().stream().map(NodeAddress::getId).collect(Collectors.toList());

		s3KeyComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Instant o1TimeStamp = Utility.parseToInstant(Utility.parseS3SummaryKey(o1).getMiddle());
				Instant o2TimeStamp = Utility.parseToInstant(Utility.parseS3SummaryKey(o2).getMiddle());
				if (o1TimeStamp == null) return -1;
				if (o2TimeStamp == null) return 1;
				return o1TimeStamp.compareTo(o2TimeStamp);
			}
		};

		fileNameComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Instant o1TimeStamp = Utility.getInstantFromFileName(o1);
				Instant o2TimeStamp = Utility.getInstantFromFileName(o2);
				return o1TimeStamp.compareTo(o2TimeStamp);
			}
		};

		pathComparator = new Comparator<Path>() {
			@Override
			public int compare(Path p1, Path p2) {
				return p1.toString().compareTo(p2.toString());
			}
		};

        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
	}

	private boolean isNeededSigFile(String s3ObjectKey, DownloadType type) {
		boolean result = false;
		switch (type) {
			case BALANCE:
				result = Utility.isBalanceSigFile(s3ObjectKey);
				break;
			case RCD:
				result = Utility.isRecordSigFile(s3ObjectKey);
				break;
			case EVENT:
				result = Utility.isEventStreamSigFile(s3ObjectKey);
				break;
			default:
				break;
		}
		return result;
	}

	/**
	 *  If type is DownloadType.RCD:
	 * 		Download all .rcd_sig files with timestamp later than lastValidRcdFileName
	 * 		Validate each .rcd_sig file with corresponding node's PubKey
	 * 		Put valid .rcd_sig into HashMap<String, List<File>>
	 *
	 *  If type is DownloadType.BALANCE:
	 * 		Download all _Balances.csv_sig files with timestamp later than lastValidBalanceFileName
	 * 		Validate each _Balances.csv_sig file with corresponding node's PubKey
	 * 		Put valid _Balances.csv_sig into HashMap<String, List<File>>
	 *
	 * 	@return
	 * 	If type is DownloadType.RCD:
	 * 		key: .rcd_sig file name
	 * 		value: a list of .rcd_sig files with the same name and from different nodes folder;
	 *
	 *  If type is DownloadType.BALANCE:
	 * 		key: _Balances.csv_sig file name
	 * 		value: a list of _Balances.csv_sig files with the same name and from different nodes folder;
	 * @throws Exception
	 */
	protected Map<String, List<File>> downloadSigFiles(DownloadType type) throws Exception {
		String s3Prefix = downloaderProperties.getPrefix();
		String lastValidFileName = null;
		switch (type) {
			case RCD:
				lastValidFileName = applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
				break;

			case BALANCE:
				s3Prefix = "accountBalances/balance";
				lastValidFileName = applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE);
				break;

			case EVENT:
				lastValidFileName = applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE);
				break;

			default:
				throw new UnsupportedOperationException("Invalid DownloadType " + type);
		}

		final var sigFilesMap = new ConcurrentHashMap<String, List<File>>();

		// refresh node account ids
		nodeAccountIds = networkAddressBook.load().stream().map(NodeAddress::getId).collect(Collectors.toList());
		List<Callable<Object>> tasks = new ArrayList<Callable<Object>>(nodeAccountIds.size());
		final var totalDownloads = new AtomicInteger();
		/**
		 * For each node, create a thread that will make S3 ListObject requests as many times as necessary to
		 * start maxDownloads download operations.
		 */
		for (String nodeAccountId : nodeAccountIds) {
			String finalLastValidFileName = lastValidFileName;
			String finalS3Prefix = s3Prefix;
			tasks.add(Executors.callable(() -> {
				log.debug("Downloading {} signature files for node {} created after file {}", type, nodeAccountId, finalLastValidFileName);
				// Get a list of objects in the bucket, 100 at a time
				String prefix = finalS3Prefix + nodeAccountId + "/";
				int downloadCount = 0;
				int downloadMax = downloaderProperties.getBatchSize();
				Stopwatch stopwatch = Stopwatch.createStarted();

				try {
					// batchSize (number of items we plan do download in a single batch) times 2 for file + sig
					// + 5 for any other files we may skip
					final var listSize = (downloaderProperties.getBatchSize() * 2) + 5;
					ListObjectsRequest listRequest = new ListObjectsRequest()
							.withBucketName(downloaderProperties.getCommon().getBucketName())
							.withPrefix(prefix)
							.withDelimiter("/")
							.withMarker(prefix + finalLastValidFileName)
							.withMaxKeys(listSize);
					ObjectListing objects = transferManager.getAmazonS3Client().listObjects(listRequest);
					var pendingDownloads = new LinkedList<PendingDownload>();
					// Loop through the list of remote files beginning a download for each relevant sig file.

					while (downloadCount <= downloadMax) {
						List<S3ObjectSummary> summaries = objects.getObjectSummaries();
						for (S3ObjectSummary summary : summaries) {
							if (downloadCount >= downloadMax) {
								break;
							}

							String s3ObjectKey = summary.getKey();

							if (isNeededSigFile(s3ObjectKey, type) &&
									(s3KeyComparator.compare(s3ObjectKey, prefix + finalLastValidFileName) > 0 || finalLastValidFileName.isEmpty())) {
								Path saveTarget = downloaderProperties.getStreamPath().getParent().resolve(s3ObjectKey);
								try {
									pendingDownloads.add(saveToLocalAsync(s3ObjectKey, saveTarget));
									if (downloadMax != 0) {
										downloadCount++;
										totalDownloads.incrementAndGet();
									}
								} catch (Exception ex) {
									log.error("Failed downloading {}", s3ObjectKey, ex);
									return;
								}
							}
						}
						if (downloadCount >= downloadMax) {
							break;
						} else if (objects.isTruncated()) {
							objects = transferManager.getAmazonS3Client().listNextBatchOfObjects(objects);
						} else {
							break;
						}
					}

					/**
					 * With the list of pending downloads - wait for them to complete and add them to the list
					 * of downloaded signature files.
					 */
					var ref = new Object() {
						int count = 0;
					};
					pendingDownloads.forEach((pd) -> {
						try {
							if (pd.waitForCompletion()) {
								ref.count++;
								File sigFile = pd.getFile();
								String fileName = sigFile.getName();
								sigFilesMap.putIfAbsent(fileName, Collections.synchronizedList(new ArrayList<>()));
								List<File> files = sigFilesMap.get(fileName);
								files.add(sigFile);
							}
						} catch (InterruptedException ex) {
							log.error("Failed downloading {} in {}", pd.getS3key(), pd.getStopwatch(), ex);
						}
					});
					if (ref.count > 0) {
						log.info("Downloaded {} {} signatures for node {} in {}", ref.count, type, nodeAccountId, stopwatch);
					}
				} catch (Exception e) {
					log.error("Error downloading {} signature files for node {} after {}", type, nodeAccountId, stopwatch, e);
				}
			}));
		}

		// Wait for all tasks to complete.
		// invokeAll() does return Futures, but it waits for all to complete (so they're returned in a completed state).
		Stopwatch stopwatch = Stopwatch.createStarted();
		signatureDownloadThreadPool.invokeAll(tasks);
		if (totalDownloads.get() > 0) {
			double rate = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			if (0 == rate) rate = 1;
			rate = 1000.0 * totalDownloads.get() / rate;
			log.info("Downloaded {} signatures in {} ({}/s)", totalDownloads, stopwatch, rate);
		}
		return sigFilesMap;
	}

	/**
	 * Returns a PendingDownload for which the caller can waitForCompletion() to wait for the download to complete.
	 * This either queues or begins the download (depending on the AWS TransferManager).
	 * @param s3ObjectKey
	 * @param localFile
	 * @return
	 */
	private PendingDownload saveToLocalAsync(String s3ObjectKey, Path localFile)
			throws Exception {
        File file = localFile.toFile();
		Download download = transferManager.download(downloaderProperties.getCommon().getBucketName(), s3ObjectKey, file);
		return new PendingDownload(download, file, s3ObjectKey);
	}

	/**
	 * Moves a file from one location to another
	 * boolean: true if file moved successfully
	 * Note: The method doesn't check if source file or destination directory exist to avoid
	 * repeated checks that could hurt performance
	 * @param sourceFile
	 * @param destinationFile
	 * @return boolean
	 */
	protected boolean moveFile(File sourceFile, File destinationFile) {
		try {
			// not checking if file exists to help with performance
			// assumption is caller has created the destination file folder
            log.trace("Moving {} to {}", sourceFile, destinationFile);
			Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			log.error("File move from {} to {} failed", sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath(), e);
			return false;
		}
	}

	protected Pair<Boolean, File> downloadFile(DownloadType downloadType, File sigFile, Path targetDir) {
		String fileName = "";
		String s3Prefix = downloaderProperties.getPrefix();

		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String sigFileName = sigFile.getName();

		switch (downloadType) {
			case BALANCE:
				fileName = sigFileName.replace("_Balances.csv_sig", "_Balances.csv");
				break;
			case EVENT:
				fileName = sigFileName.replace(".evts_sig", ".evts");
				break;
			case RCD:
				fileName = sigFileName.replace(".rcd_sig", ".rcd");
				break;
		}
		String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;

		Path localFile = targetDir.resolve(fileName);
		try {
			var pendingDownload = saveToLocalAsync(s3ObjectKey, localFile);
			pendingDownload.waitForCompletion();
			return Pair.of(pendingDownload.isDownloadSuccessful(), pendingDownload.getFile());
		} catch (Exception ex) {
			log.error("Failed downloading {}", s3ObjectKey, ex);
			return Pair.of(false, null);
		}
	}
}
