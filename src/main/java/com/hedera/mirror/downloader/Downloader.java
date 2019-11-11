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

import com.google.common.base.Stopwatch;

import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.domain.NodeAddress;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.utilities.ShutdownHelper;
import com.hedera.utilities.Utility;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class Downloader {
	protected final Logger log = LogManager.getLogger(getClass());

	private final S3AsyncClient s3Client;
	private List<String> nodeAccountIds;
	private final ApplicationStatusRepository applicationStatusRepository;
    private final NetworkAddressBook networkAddressBook;
	private final DownloaderProperties downloaderProperties;
    // Thread pool used one per node during the download process for signatures.
	private final ExecutorService signatureDownloadThreadPool;

    public Downloader(S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
                      NetworkAddressBook networkAddressBook, DownloaderProperties downloaderProperties) {
	    this.s3Client = s3Client;
		this.applicationStatusRepository = applicationStatusRepository;
		this.networkAddressBook = networkAddressBook;
		this.downloaderProperties = downloaderProperties;
		signatureDownloadThreadPool = Executors.newFixedThreadPool(downloaderProperties.getThreads());
		nodeAccountIds = networkAddressBook.load().stream().map(NodeAddress::getId).collect(Collectors.toList());
        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
	}

	protected void downloadNextBatch() {
        try {
            if (!downloaderProperties.isEnabled()) {
                return;
            }
            if (ShutdownHelper.isStopping()) {
                return;
            }
            final var sigFilesMap = downloadSigFiles();
            // Verify signature files and download corresponding files of valid signature files
            verifySigsAndDownloadDataFiles(sigFilesMap);
        } catch (Exception e) {
            log.error("Error downloading files", e);
        }
    }

    /**
     * 	Download all sig files (*.rcd_sig for records, *_Balances.csv_sig for balances) with timestamp later than
     * 	lastValid<Type>FileName
     * 	Validate each file with corresponding node's PubKey.
     * 	Put valid files into HashMap<String, List<File>>

     *  @return
     *      key: sig file name
     *      value: a list of sig files with the same name and from different nodes folder;
     */
	private Map<String, List<File>> downloadSigFiles() throws InterruptedException {
		String lastValidFileName = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileKey());
        // foo.rcd < foo.rcd_sig. If we read foo.rcd from application stats, we have to start listing from
        // next to 'foo.rcd_sig'.
        String lastValidSigFileName = lastValidFileName.isEmpty() ? "" : lastValidFileName + "_sig";

		final var sigFilesMap = new ConcurrentHashMap<String, List<File>>();

		// refresh node account ids
		nodeAccountIds = networkAddressBook.load().stream().map(NodeAddress::getId).collect(Collectors.toList());
		List<Callable<Object>> tasks = new ArrayList<>(nodeAccountIds.size());
		final var totalDownloads = new AtomicInteger();
		/**
		 * For each node, create a thread that will make S3 ListObject requests as many times as necessary to
		 * start maxDownloads download operations.
		 */
        final Path dataPath = downloaderProperties.getStreamPath().getParent();
		for (String nodeAccountId : nodeAccountIds) {
			tasks.add(Executors.callable(() -> {
				log.debug("Downloading signature files for node {} created after file {}", nodeAccountId, lastValidSigFileName);
                Stopwatch stopwatch = Stopwatch.createStarted();
				// Get a list of objects in the bucket, 100 at a time
				String s3prefix = downloaderProperties.getPrefix() + nodeAccountId + "/";

                // s3prefix is of format "X/Y/" (e.g. "recordstreams/record0.0.3/"), so a replace here with use of
                // Paths in rest of the code ensures platform compatibility. More involved way would splitting 'prefix'
                // in all DownloaderProperties implementations to two values and then join then separately for S3 and
                // for local filesystem.
                final Path sigFilesDir = dataPath.resolve(s3prefix.replace('/', File.separatorChar));
                // Ensure the directory for downloading sig files exists.
                Utility.ensureDirectory(sigFilesDir);

                try {
                    // batchSize (number of items we plan do download in a single batch) times 2 for file + sig.
                    final var listSize = (downloaderProperties.getBatchSize() * 2);
                    // Not using ListObjectsV2Request because it does not work with GCP.
                    ListObjectsRequest listRequest = ListObjectsRequest.builder()
                            .bucket(downloaderProperties.getCommon().getBucketName())
                            .prefix(s3prefix)
                            .delimiter("/")
                            .marker(s3prefix + lastValidSigFileName)
                            .maxKeys(listSize)
                            .build();
                    CompletableFuture<ListObjectsResponse> response = s3Client.listObjects(listRequest);
                    var pendingDownloads = new ArrayList<PendingDownload>(downloaderProperties.getBatchSize());
                    // Loop through the list of remote files beginning a download for each relevant sig file
                    // Note:
                    // lastValidSigFileName specified as marker above is not returned in these results by AWS S3.
                    // However, it is returned by mockS3 implementation we use in our tests.
                    for (S3Object content : response.get().contents()) {
                        String s3ObjectKey = content.key();
                        if (s3ObjectKey.endsWith("_sig")) {
                            String fileName = s3ObjectKey.substring(s3ObjectKey.lastIndexOf("/") + 1);
                            Path saveTarget = sigFilesDir.resolve(fileName);
                            pendingDownloads.add(saveToLocalAsync(s3ObjectKey, saveTarget));
                            totalDownloads.incrementAndGet();
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
			var rate = (int)(1000000.0 * totalDownloads.get() / stopwatch.elapsed(TimeUnit.MICROSECONDS));
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
    private PendingDownload saveToLocalAsync(String s3ObjectKey, Path localFile) {
        File file = localFile.toFile();
        // If process stops abruptly and is restarted, it's possible we try to re-download some of the files which
        // already exist on disk because lastValidFileName wasn't updated. AsyncFileResponseTransformer throws
        // exceptions if a file already exists, rather then silently overwrite it. So following check are to avoid
        // log spam of java.nio.file.FileAlreadyExistsException, not for optimization purpose. We can't use old file
        // because it may be half written or it maybe a data file from a node which doesn't match the hash.
        // Overwriting is the only way forward.
        // This is okay for now since we are moving away from storing downloaded S3 data in files.
        if (file.exists()) {
            boolean success = file.delete();
            if (!success) {
                log.error("Failed to delete the file {}. Expect long stack trace with FileAlreadyExistsException below",
                        file);
            }
        }
        var future = s3Client.getObject(
                GetObjectRequest.builder().bucket(downloaderProperties.getCommon().getBucketName()).key(s3ObjectKey).build(),
                AsyncResponseTransformer.toFile(file));
        return new PendingDownload(future, file, s3ObjectKey);
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
	private boolean moveFile(File sourceFile, File destinationFile) {
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
    private void verifySigsAndDownloadDataFiles(Map<String, List<File>> sigFilesMap) {
        // reload address book and keys in case it has been updated by RecordFileLogger
        NodeSignatureVerifier verifier = new NodeSignatureVerifier(networkAddressBook);
        Path validPath = downloaderProperties.getValidPath();

        List<String> sigFileNames = new ArrayList<>(sigFilesMap.keySet());
        // sort in increasing order of timestamp, so that we process files in the order they are written.
        // It's very important for record and event files because they form immutable linked list by include one file's
        // hash into next file.
        Collections.sort(sigFileNames);

        for (String sigFileName : sigFileNames) {
            if (ShutdownHelper.isStopping()) {
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
            Pair<byte[], List<File>> hashAndValidSigFiles = verifier.verifySignatureFiles(sigFiles);
            final byte[] validHash = hashAndValidSigFiles.getLeft();
            for (File validSigFileName : hashAndValidSigFiles.getRight()) {
                if (ShutdownHelper.isStopping()) {
                    return;
                }
                log.debug("Verified signature file matches at least 2/3 of nodes: {}", sigFileName);

                try {
                    File signedDataFile = downloadSignedDataFile(validSigFileName);
                    if (signedDataFile != null && Utility.hashMatch(validHash, signedDataFile)) {
                        log.debug("Downloaded data file {} corresponding to verified hash", signedDataFile.getName());
                        // Check that file is newer than last valid downloaded file.
                        // Additionally, if the file type uses prevFileHash based linking, verify that new file is next in
                        // the sequence.
                        if (verifyHashChain(signedDataFile)) {
                            // move the file to the valid directory
                            File destination = validPath.resolve(signedDataFile.getName()).toFile();
                            if (moveFile(signedDataFile, destination)) {
                                log.debug("Successfully moved file from {} to {}", signedDataFile, destination);
                                if (getLastValidDownloadedFileHashKey() != null) {
                                    applicationStatusRepository.updateStatusValue(getLastValidDownloadedFileHashKey(),
                                            Utility.bytesToHex(validHash));
                                }
                                applicationStatusRepository
                                        .updateStatusValue(getLastValidDownloadedFileKey(), destination.getName());
                                valid = true;
                                break;
                            }
                        }
                    } else if (signedDataFile != null) {
                        log.warn("Hash doesn't match the hash contained in valid signature file. Will try to download" +
                                " a file with same timestamp from other nodes and check the Hash: {}", signedDataFile);
                    }
                } catch (Exception e) {
                    log.error("Error downloading data file corresponding to {}", sigFileName, e);
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
    protected boolean verifyHashChain(File file) {
        String filePath = file.getAbsolutePath();
        String lastValidFileHash = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileHashKey());
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

    private File downloadSignedDataFile(File sigFile) {
        String fileName = sigFile.getName().replace("_sig", "");
        String s3Prefix = downloaderProperties.getPrefix();

		String nodeAccountId = Utility.getAccountIDStringFromFilePath(sigFile.getPath());
		String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;

		Path localFile = downloaderProperties.getTempPath().resolve(fileName);
		try {
			var pendingDownload = saveToLocalAsync(s3ObjectKey, localFile);
			pendingDownload.waitForCompletion();
			if (pendingDownload.isDownloadSuccessful()) {
			    return pendingDownload.getFile();
            } else {
                log.error("Failed downloading {} from node {}", s3ObjectKey, nodeAccountId);
            }
		} catch (Exception ex) {
            log.error("Failed downloading {} from node {}", s3ObjectKey, nodeAccountId, ex);
		}
        return null;
    }

    protected abstract ApplicationStatusCode getLastValidDownloadedFileKey();
    protected abstract ApplicationStatusCode getLastValidDownloadedFileHashKey();
    protected abstract ApplicationStatusCode getBypassHashKey();
    protected abstract String getPrevFileHash(String filePath);
    public abstract void download();
}
