package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.model.S3Object;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.NodeAddress;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

public abstract class Downloader {

    protected final Logger log = LogManager.getLogger(getClass());
    private final S3AsyncClient s3Client;
    private final NetworkAddressBook networkAddressBook;
    private final ExecutorService signatureDownloadThreadPool; // One per node during the signature download process
    protected final ApplicationStatusRepository applicationStatusRepository;
    protected final DownloaderProperties downloaderProperties;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final MirrorProperties mirrorProperties;

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Counter.Builder signatureVerificationMetric;
    private final Timer.Builder streamVerificationMetric;
    protected final Timer downloadLatencyMetric;
    protected final Timer streamCloseMetric;

    public Downloader(S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
                      NetworkAddressBook networkAddressBook, DownloaderProperties downloaderProperties,
                      MeterRegistry meterRegistry) {
        this.s3Client = s3Client;
        this.applicationStatusRepository = applicationStatusRepository;
        this.networkAddressBook = networkAddressBook;
        this.downloaderProperties = downloaderProperties;
        this.meterRegistry = meterRegistry;
        signatureDownloadThreadPool = Executors.newFixedThreadPool(downloaderProperties.getThreads());
        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
        commonDownloaderProperties = downloaderProperties.getCommon();
        mirrorProperties = downloaderProperties.getMirrorProperties();

        // Metrics
        signatureVerificationMetric = Counter.builder("hedera.mirror.download.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("type", downloaderProperties.getStreamType().toString());

        streamVerificationMetric = Timer.builder("hedera.mirror.download.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", downloaderProperties.getStreamType().toString());

        downloadLatencyMetric = Timer.builder("hedera.mirror.download.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was downloaded and verified")
                .tag("type", downloaderProperties.getStreamType().toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder("hedera.mirror.stream.close.latency")
                .description("The difference between the consensus time of the last and first transaction in the " +
                        "stream file")
                .tag("type", downloaderProperties.getStreamType().toString())
                .register(meterRegistry);
    }

    protected void downloadNextBatch() {
        try {
            if (!downloaderProperties.isEnabled()) {
                return;
            }
            if (ShutdownHelper.isStopping()) {
                return;
            }

            var sigFilesMap = downloadSigFiles();

            // Following is a cost optimization to not unnecessarily list the public demo bucket once complete
            if (sigFilesMap.isEmpty() && mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.DEMO) {
                downloaderProperties.setEnabled(false);
                log.warn("Disabled polling after downloading all files in demo bucket");
            }

            // Verify signature files and download corresponding files of valid signature files
            verifySigsAndDownloadDataFiles(sigFilesMap);
        } catch (SignatureVerificationException e) {
            log.warn(e.getMessage());
        } catch (Exception e) {
            log.error("Error downloading files", e);
        }
    }

    /**
     * Download all signature files with a timestamp later than the last valid file. Put signature files into a
     * multi-map sorted and grouped by the expected closing interval.
     *
     * @return a multi-map of expected close interval to signature file objects from different nodes
     */
    private Multimap<Long, FileStreamSignature> downloadSigFiles() throws InterruptedException {
        String lastValidFileName = applicationStatusRepository.findByStatusCode(getLastValidDownloadedFileKey());
        // foo.rcd < foo.rcd_sig. If we read foo.rcd from application stats, we have to start listing from
        // next to 'foo.rcd_sig'.
        String lastValidSigFileName = lastValidFileName.isEmpty() ? "" : lastValidFileName + "_sig";
        Multimap<Long, FileStreamSignature> sigFilesMap = Multimaps
                .synchronizedSortedSetMultimap(TreeMultimap.create());
        Set<String> nodeAccountIds = networkAddressBook.getAddresses()
                .stream()
                .map(NodeAddress::getId)
                .collect(Collectors.toSet());
        List<Callable<Object>> tasks = new ArrayList<>(nodeAccountIds.size());
        var totalDownloads = new AtomicInteger();
        long closeInterval = downloaderProperties.getCloseInterval().toNanos();
        long lastValidTimestamp = Utility.getTimestampFromFilename(lastValidFileName);
        log.info("Downloading signature files created after file: {}", lastValidSigFileName);

        /**
         * For each node, create a thread that will make S3 ListObject requests as many times as necessary to
         * start maxDownloads download operations.
         */
        for (String nodeAccountId : nodeAccountIds) {
            tasks.add(Executors.callable(() -> {
                Stopwatch stopwatch = Stopwatch.createStarted();
                // Get a list of objects in the bucket, 100 at a time
                String s3Prefix = downloaderProperties.getPrefix() + nodeAccountId + "/";
                Path sigFilesDir = downloaderProperties.getTempPath().resolve(nodeAccountId);
                Utility.ensureDirectory(sigFilesDir);

                try {
                    // batchSize (number of items we plan do download in a single batch) times 2 for file + sig.
                    var listSize = (downloaderProperties.getBatchSize() * 2);
                    // Not using ListObjectsV2Request because it does not work with GCP.
                    ListObjectsRequest listRequest = ListObjectsRequest.builder()
                            .bucket(commonDownloaderProperties.getBucketName())
                            .prefix(s3Prefix)
                            .delimiter("/")
                            .marker(s3Prefix + lastValidSigFileName)
                            .maxKeys(listSize)
                            .requestPayer(RequestPayer.REQUESTER)
                            .build();
                    CompletableFuture<ListObjectsResponse> response = s3Client.listObjects(listRequest);
                    Collection<PendingDownload> pendingDownloads = new ArrayList<>(downloaderProperties.getBatchSize());

                    // Loop through the list of remote files beginning a download for each relevant sig file
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
                    AtomicLong count = new AtomicLong();
                    pendingDownloads.forEach(pendingDownload -> {
                        try {
                            if (pendingDownload.waitForCompletion()) {
                                count.incrementAndGet();
                                File sigFile = pendingDownload.getFile();
                                FileStreamSignature fileStreamSignature = new FileStreamSignature();
                                fileStreamSignature.setFile(sigFile);
                                fileStreamSignature.setNode(nodeAccountId);
                                long groupTimestamp = getGroupTimestamp(lastValidTimestamp, closeInterval,
                                        fileStreamSignature);

                                if (groupTimestamp > 0) {
                                    sigFilesMap.put(groupTimestamp, fileStreamSignature);
                                } else {
                                    log.warn("Ignoring signature associated with the previously processed stream " +
                                            "file: {}", fileStreamSignature);
                                }
                            }
                        } catch (InterruptedException ex) {
                            log.warn("Failed downloading {} in {}", pendingDownload.getS3key(),
                                    pendingDownload.getStopwatch(), ex);
                        }
                    });
                    if (count.get() > 0) {
                        log.info("Downloaded {} signatures for node {} in {}", count.get(), nodeAccountId, stopwatch);
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
            var rate = (int) (1000000.0 * totalDownloads.get() / stopwatch.elapsed(TimeUnit.MICROSECONDS));
            log.info("Downloaded {} signatures in {} ({}/s)", totalDownloads, stopwatch, rate);
        }
        return sigFilesMap;
    }

    /**
     * Partitions signature files into a time range [T + I/2, T + I * 3/2) where T is the last valid timestamp and I is
     * the stream close interval. Calculates the start time of the nearest range that includes the timestamp of the
     * current file.
     *
     * @param lastValidTimestamp the last timestamp of the signature that was successfully verified
     * @param closeInterval      how often the file closes
     * @param signature          domain object
     * @return a timestamp representing the start time of the bucket this signature should be associated with
     */
    private long getGroupTimestamp(long lastValidTimestamp, long closeInterval, FileStreamSignature signature) {
        long currentTimestamp = Utility.getTimestampFromFilename(signature.getFile().getName());
        long groupTimestamp = 0;

        // Initial file has no previous so we can't calculate offset from that
        if (lastValidTimestamp <= 0) {
            groupTimestamp = currentTimestamp;
        } else {
            double interval = (double) (currentTimestamp - lastValidTimestamp);
            long bucket = Math.round(interval / closeInterval);

            if (bucket > 0) {
                groupTimestamp = lastValidTimestamp + bucket * closeInterval + closeInterval / 2;
            }
        }

        return groupTimestamp;
    }

    /**
     * Returns a PendingDownload for which the caller can waitForCompletion() to wait for the download to complete. This
     * either queues or begins the download (depending on the AWS TransferManager).
     *
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

        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3ObjectKey)
                .requestPayer(RequestPayer.REQUESTER)
                .build();
        var future = s3Client.getObject(request, AsyncResponseTransformer.toFile(localFile));
        return new PendingDownload(future, file, s3ObjectKey);
    }

    /**
     * Moves a file from one location to another. The method doesn't check if source file or destination directory exist
     * to avoid repeated checks that could hurt performance.
     *
     * @param sourceFile
     * @param destinationFile
     * @return whether the file was moved successfully
     */
    private boolean moveFile(File sourceFile, File destinationFile) {
        try {
            Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
            log.trace("Moved {} to {}", sourceFile, destinationFile);
            return true;
        } catch (IOException e) {
            log.error("File move from {} to {} failed", sourceFile.getAbsolutePath(), destinationFile
                    .getAbsolutePath(), e);
            return false;
        }
    }

    private void moveSignatureFile(FileStreamSignature signature) {
        if (downloaderProperties.isKeepSignatures()) {
            Path destination = downloaderProperties.getSignaturesPath().resolve(signature.getNode());
            Utility.archiveFile(signature.getFile(), destination);
        } else {
            FileUtils.deleteQuietly(signature.getFile());
        }
    }

    /**
     * For each group of signature files with the same file name: (1) verify that the signature files are signed by
     * corresponding node's PublicKey; (2) For valid signature files, we compare their Hashes to see if at least 1/3 of
     * hashes match. If they do, we download the corresponding data file from a node folder which has valid signature
     * file. (3) compare the hash of data file with Hash which has been agreed on by valid signatures, if match, move
     * the data file into `valid` directory; else download the data file from other valid node folder and compare the
     * hash until we find a match.
     */
    private void verifySigsAndDownloadDataFiles(Multimap<Long, FileStreamSignature> sigFilesMap) {
        NodeSignatureVerifier nodeSignatureVerifier = new NodeSignatureVerifier(networkAddressBook);
        Path validPath = downloaderProperties.getValidPath();

        for (Long groupId : sigFilesMap.keySet()) {
            if (ShutdownHelper.isStopping()) {
                return;
            }

            Instant startTime = Instant.now();
            Collection<FileStreamSignature> signatures = sigFilesMap.get(groupId);
            boolean valid = false;

            try {
                nodeSignatureVerifier.verify(signatures);
            } finally {
                for (FileStreamSignature signature : signatures) {
                    EntityId nodeAccountId = EntityId.of(signature.getNode(), EntityTypeEnum.ACCOUNT);
                    signatureVerificationMetric.tag("nodeAccount", nodeAccountId.getEntityNum().toString())
                            .tag("realm", nodeAccountId.getRealmNum().toString())
                            .tag("shard", nodeAccountId.getShardNum().toString())
                            .tag("status", signature.getStatus().toString())
                            .register(meterRegistry)
                            .increment();
                }
            }

            for (FileStreamSignature signature : signatures) {
                if (ShutdownHelper.isStopping()) {
                    return;
                }

                // Ignore signatures that didn't validate or weren't in the majority
                if (signature.getStatus() != FileStreamSignature.SignatureStatus.CONSENSUS_REACHED) {
                    continue;
                }

                try {
                    File signedDataFile = downloadSignedDataFile(signature.getFile(), signature.getNode());
                    if (signedDataFile == null) {
                        continue;
                    }
                    if (verifyDataFile(signedDataFile, signature.getHash())) {
                        // move the file to the valid directory
                        File destination = validPath.resolve(signedDataFile.getName()).toFile();
                        if (moveFile(signedDataFile, destination)) {
                            if (getLastValidDownloadedFileHashKey() != null) {
                                applicationStatusRepository.updateStatusValue(getLastValidDownloadedFileHashKey(),
                                        signature.getHashAsHex());
                            }
                            applicationStatusRepository
                                    .updateStatusValue(getLastValidDownloadedFileKey(), destination.getName());
                            valid = true;
                            signatures.forEach(this::moveSignatureFile);
                            break;
                        }
                    } else {
                        log.warn("Verification of data file {} from node {} failed. Will retry another node",
                                signedDataFile.getName(), signature.getNode());
                    }
                } catch (Exception e) {
                    log.error("Error downloading data file from node {} corresponding to {}. Will retry another node",
                            signature.getNode(), signature.getFile().getName(), e);
                }
            }

            if (!valid) {
                log.error("File could not be verified by at least 1/3 of nodes: {}", signatures);
            }

            streamVerificationMetric.tag("success", String.valueOf(valid))
                    .register(meterRegistry)
                    .record(Duration.between(startTime, Instant.now()));
        }
    }

    private File downloadSignedDataFile(File sigFile, String nodeAccountId) {
        String fileName = sigFile.getName().replace("_sig", "");
        String s3Prefix = downloaderProperties.getPrefix();

        String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;

        Path localFile = downloaderProperties.getTempPath().resolve(fileName);
        try {
            var pendingDownload = saveToLocalAsync(s3ObjectKey, localFile);
            pendingDownload.waitForCompletion();
            if (pendingDownload.isDownloadSuccessful()) {
                return pendingDownload.getFile();
            } else {
                log.warn("Failed downloading {} from node {}", s3ObjectKey, nodeAccountId);
            }
        } catch (Exception ex) {
            log.warn("Failed downloading {} from node {}", s3ObjectKey, nodeAccountId, ex);
        }
        return null;
    }

    protected abstract ApplicationStatusCode getLastValidDownloadedFileKey();

    protected abstract ApplicationStatusCode getLastValidDownloadedFileHashKey();

    protected abstract boolean verifyDataFile(File file, byte[] signedHash);

    public abstract void download();
}
