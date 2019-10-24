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

import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;

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
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /**
     * 	Download all sig files (*.rcd_sig for records, *_Balances.csv_sig for balances) with timestamp later than
     * 	lastValid<Type>FileName
     * 	Validate each file with corresponding node's PubKey.
     * 	Put valid files into HashMap<String, List<File>>

     *  @return
     *      key: sig file name
     *      value: a list of sig files with the same name and from different nodes folder;
     * @throws Exception
     */
	protected Map<String, List<File>> downloadSigFiles() throws InterruptedException {
		String s3Prefix = downloaderProperties.getPrefix();
		String lastValidFileName = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileKey());

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
			tasks.add(Executors.callable(() -> {
				log.debug("Downloading signature files for node {} created after file {}", nodeAccountId, lastValidFileName);
				// Get a list of objects in the bucket, 100 at a time
				String prefix = s3Prefix + nodeAccountId + "/";
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
							.withMarker(prefix + lastValidFileName)
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

							if (s3ObjectKey.endsWith("_sig") &&
									(s3KeyComparator.compare(s3ObjectKey, prefix + lastValidFileName) > 0 || lastValidFileName.isEmpty())) {
								Path saveTarget = downloaderProperties.getStreamPath().getParent().resolve(s3ObjectKey);
								try {
									pendingDownloads.add(saveToLocalAsync(s3ObjectKey, saveTarget));
                                    downloadCount++;
                                    totalDownloads.incrementAndGet();
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

					/*
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
						log.info("Downloaded {} signatures for node {} in {}", ref.count, nodeAccountId, stopwatch);
					}
				} catch (Exception e) {
					log.error("Error downloading signature files for node {} after {}", nodeAccountId, stopwatch, e);
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

    /**
     *  For each group of signature Files with the same file name:
     *  (1) verify that the signature files are signed by corresponding node's PublicKey;
     *  (2) For valid signature files, we compare their Hashes to see if more than 2/3 Hashes matches.
     *  If more than 2/3 Hashes matches, we download the corresponding data file from a node folder which has valid
     *  signature file.
     *  (3) compare the Hash of data file with Hash which has been agreed on by valid signatures, if match, move the
     *  data file into `valid` directory; else download the data file from other valid node folder, and compare the
     *  Hash until find a match one
     * @param sigFilesMap
     */
    protected void verifySigsAndDownloadDataFiles(Map<String, List<File>> sigFilesMap) {
        String lastValidFileName =
                applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileKey());

        // reload address book and keys
        NodeSignatureVerifier verifier = new NodeSignatureVerifier(networkAddressBook);

        List<String> sigFileNames = new ArrayList<>(sigFilesMap.keySet());
        Collections.sort(sigFileNames);

        for (String sigFileName : sigFileNames) {
            if (Utility.checkStopFile()) {
                log.info("Stop file found, stopping");
                return;
            }

            List<File> sigFiles = sigFilesMap.get(sigFileName);
            boolean valid = false;

            // If the number of sigFiles is not greater than 2/3 of number of nodes, we don't need to verify them
            if (sigFiles == null || !Utility.greaterThanSuperMajorityNum(sigFiles.size(), nodeAccountIds.size())) {
                log.warn("Signature file count does not exceed 2/3 of nodes");
                continue;
            }

            // validSigFiles are signed by node'key and contains the same Hash which has been agreed by more than 2/3 nodes
            Pair<byte[], List<File>> hashAndvalidSigFiles = verifier.verifySignatureFiles(sigFiles);
            final byte[] validHash = hashAndvalidSigFiles.getLeft();
            for (File validSigFile : hashAndvalidSigFiles.getRight()) {
                if (Utility.checkStopFile()) {
                    log.info("Stop file found, stopping");
                    return;
                }

                Pair<Boolean, File> fileResult = downloadFile(validSigFile);
                File file = fileResult.getRight();
                if (file != null && Utility.hashMatch(validHash, file)) {
                    log.debug("Verified signature file matches at least 2/3 of nodes: {}", sigFileName);
                    // Check that file is newer than last valid downloaded file.
                    // Additionally, if the file type uses prevFileHash based linking, verify that new file is next in
                    // the sequence.
                    // TODO(reviewers): Requesting extra careful look at this condition. Thanks
                    if ((lastValidFileName.isEmpty()
                            || fileNameComparator.compare(lastValidFileName, file.getName()) < 0)
                            && (!shouldVerifyHashChain() || verifyHashChain(file))) {
                        // move the file to the valid directory
                        File destination = downloaderProperties.getValidPath().resolve(file.getName()).toFile();
                        if (moveFile(file, destination)) {
                            log.debug("Successfully moved file from {} to {}", file, destination);
                            if (shouldVerifyHashChain()) {
                                String hash = Utility.bytesToHex(Utility.getFileHash(destination.getAbsolutePath()));
                                applicationStatusRepository.updateStatusValue(getLastValidDownloadedFileHashKey(), hash);
                            }
                            applicationStatusRepository.updateStatusValue(getLastValidDownloadedFileKey(), destination.getName());
                            valid = true;
                            break;
                        }
                    }
                } else if (file != null) {
                    log.warn("Hash doesn't match the hash contained in valid signature file. Will try to download a file with same timestamp from other nodes and check the Hash: {}", file);
                }
            }

            if (!valid) {
                log.error("File could not be verified by at least 2/3 of nodes: {}", sigFileName);
            }
        }
    }


    /**
     * Verifies that prevFileHash in given {@code file} matches that in application repository.
     * @throws Exception
     */
    private boolean verifyHashChain(File file) {
        String filePath = file.getAbsolutePath();
        String lastValidFileHash = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileHashKey()); // todo
        String bypassMismatch = applicationStatusRepository.findByStatusCode(getBypassHashKey());
        String prevFileHash = getPrevFileHash(filePath);

        if (prevFileHash == null) {
            log.warn("Doesn't contain valid previous file hash: {}", filePath);
            return false;
        }

        if (StringUtils.isBlank(lastValidFileHash) || lastValidFileHash.equals(prevFileHash) ||
                Utility.hashIsEmpty(prevFileHash) || bypassMismatch.compareTo(file.getName()) > 0) {
            return true;
        }

        log.warn("File Hash Mismatch with previous: {}, expected {}, got {}", file.getName(), lastValidFileHash, prevFileHash);
        return false;
    }

    protected Pair<Boolean, File> downloadFile(File sigFile) {
        String fileName = sigFile.getName().replace("_sig", "");
        String s3Prefix = downloaderProperties.getPrefix();

		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;

		Path localFile = downloaderProperties.getTempPath().resolve(fileName);
		try {
			var pendingDownload = saveToLocalAsync(s3ObjectKey, localFile);
			pendingDownload.waitForCompletion();
			return Pair.of(pendingDownload.isDownloadSuccessful(), pendingDownload.getFile());
		} catch (Exception ex) {
			log.error("Failed downloading {}", s3ObjectKey, ex);
			return Pair.of(false, null);
		}
	}

    protected abstract ApplicationStatusCode getLastValidDownloadedFileKey();
    // Only used if shouldVerifyHashChain() return true
    protected abstract ApplicationStatusCode getLastValidDownloadedFileHashKey();
    // Only used if shouldVerifyHashChain() return true
    protected abstract ApplicationStatusCode getBypassHashKey();
    protected abstract boolean shouldVerifyHashChain();
    protected abstract String getPrevFileHash(String filePath);
}
