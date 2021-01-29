package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.util.Utility.verifyHashChain;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.model.S3Object;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.event.MirrorDateRangePropertiesProcessedEvent;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

public abstract class Downloader {

    protected final Logger log = LogManager.getLogger(getClass());
    private final S3AsyncClient s3Client;
    private final AddressBookService addressBookService;
    private final ExecutorService signatureDownloadThreadPool; // One per node during the signature download process
    protected final ApplicationStatusRepository applicationStatusRepository;
    protected final DownloaderProperties downloaderProperties;
    private final MirrorProperties mirrorProperties;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final TransactionTemplate transactionTemplate;
    protected final NodeSignatureVerifier nodeSignatureVerifier;
    protected final SignatureFileReader signatureFileReader;

    protected final ApplicationStatusCode lastValidDownloadedFileKey;
    protected final ApplicationStatusCode lastValidDownloadedFileHashKey;

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Counter.Builder signatureVerificationMetric;
    private final Timer.Builder streamVerificationMetric;
    protected final Timer downloadLatencyMetric;
    protected final Timer streamCloseMetric;

    private boolean mirrorDateRangePropertiesProcessed = false;

    public Downloader(S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
                      AddressBookService addressBookService, DownloaderProperties downloaderProperties,
                      TransactionTemplate transactionTemplate, MeterRegistry meterRegistry,
                      NodeSignatureVerifier nodeSignatureVerifier, SignatureFileReader signatureFileReader) {
        this.s3Client = s3Client;
        this.applicationStatusRepository = applicationStatusRepository;
        this.addressBookService = addressBookService;
        this.downloaderProperties = downloaderProperties;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.nodeSignatureVerifier = nodeSignatureVerifier;
        signatureDownloadThreadPool = Executors.newFixedThreadPool(downloaderProperties.getThreads());
        this.signatureFileReader = signatureFileReader;
        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
        mirrorProperties = downloaderProperties.getMirrorProperties();
        commonDownloaderProperties = downloaderProperties.getCommon();

        lastValidDownloadedFileKey = downloaderProperties.getLastValidDownloadedFileKey();
        lastValidDownloadedFileHashKey = downloaderProperties.getLastValidDownloadedFileHashKey();

        StreamType streamType = downloaderProperties.getStreamType();

        // Metrics
        signatureVerificationMetric = Counter.builder("hedera.mirror.download.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("type", streamType.toString());

        streamVerificationMetric = Timer.builder("hedera.mirror.download.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", streamType.toString());

        downloadLatencyMetric = Timer.builder("hedera.mirror.download.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was downloaded and verified")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder("hedera.mirror.stream.close.latency")
                .description("The difference between the consensus time of the last and first transaction in the " +
                        "stream file")
                .tag("type", streamType.toString())
                .register(meterRegistry);
    }

    @EventListener(MirrorDateRangePropertiesProcessedEvent.class)
    public void onMirrorDateRangePropertiesProcessedEvent() {
        mirrorDateRangePropertiesProcessed = true;
    }

    protected void downloadNextBatch() {
        if (!mirrorDateRangePropertiesProcessed) {
            return;
        }

        if (!downloaderProperties.isEnabled()) {
            return;
        }
        if (ShutdownHelper.isStopping()) {
            return;
        }

        try {
            AddressBook addressBook = addressBookService.getCurrent();
            var sigFilesMap = downloadAndParseSigFiles(addressBook);

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
     * Download and parse all signature files with a timestamp later than the last valid file. Put signature files into
     * a multi-map sorted and grouped by the timestamp.
     *
     * @param addressBook the current address book
     * @return a multi-map of signature file objects from different nodes, grouped by filename
     */
    private Multimap<String, FileStreamSignature> downloadAndParseSigFiles(AddressBook addressBook)
            throws InterruptedException {
        String lastValidFileName = applicationStatusRepository.findByStatusCode(lastValidDownloadedFileKey);
        // foo.rcd < foo.rcd_sig. If we read foo.rcd from application stats, we have to start listing from
        // next to 'foo.rcd_sig'.
        String lastValidSigFileName = lastValidFileName.isEmpty() ? "" : lastValidFileName + "_sig";
        Multimap<String, FileStreamSignature> sigFilesMap = Multimaps
                .synchronizedSortedSetMultimap(TreeMultimap.create());

        Set<EntityId> nodeAccountIds = addressBook.getNodeSet();
        List<Callable<Object>> tasks = new ArrayList<>(nodeAccountIds.size());
        var totalDownloads = new AtomicInteger();
        log.info("Downloading signature files created after file: {}", lastValidSigFileName);

        /**
         * For each node, create a thread that will make S3 ListObject requests as many times as necessary to
         * start maxDownloads download operations.
         */
        for (EntityId nodeAccountId : nodeAccountIds) {
            String nodeAccountIdStr = nodeAccountId.entityIdToString();
            tasks.add(Executors.callable(() -> {
                Stopwatch stopwatch = Stopwatch.createStarted();
                // Get a list of objects in the bucket, 100 at a time
                String s3Prefix = downloaderProperties.getPrefix() + nodeAccountIdStr + "/";
                Path sigFilesDir = downloaderProperties.getTempPath().resolve(nodeAccountIdStr);
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
                     * With the list of pending downloads - wait for them to complete, parse them,  and add them to
                     * the list of signature files.
                     */
                    AtomicLong count = new AtomicLong();
                    pendingDownloads.forEach(pendingDownload -> {
                        try {
                            if (pendingDownload.waitForCompletion()) {
                                FileStreamSignature fileStreamSignature = parseSignatureFile(nodeAccountId,
                                        pendingDownload.getFile());
                                if (fileStreamSignature != null) {
                                    sigFilesMap.put(fileStreamSignature.getFile().getName(), fileStreamSignature);
                                    count.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException ex) {
                            log.warn("Failed downloading {} in {}", pendingDownload.getS3key(),
                                    pendingDownload.getStopwatch(), ex);
                            Thread.currentThread().interrupt();
                        }
                    });
                    if (count.get() > 0) {
                        log.info("Downloaded {} signatures for node {} in {}", count
                                .get(), nodeAccountIdStr, stopwatch);
                    }
                } catch (Exception e) {
                    log.error("Error downloading signature files for node {} after {}", nodeAccountIdStr, stopwatch, e);
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

    private FileStreamSignature parseSignatureFile(EntityId nodeAccountId, File sigFile) {
        try {
            FileStreamSignature fileStreamSignature = signatureFileReader.read(StreamFileData.from(sigFile));
            fileStreamSignature.setFile(sigFile);
            fileStreamSignature.setNodeAccountId(nodeAccountId);
            return fileStreamSignature;
        } catch (ImporterException ex) {
            log.warn("Failed to parse signature file {}: {}", sigFile, ex);
            return null;
        }
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
                log.error("Failed to delete the file {}", file);
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
     */
    private void moveFile(File sourceFile, File destinationFile) {
        try {
            Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
            if (log.isTraceEnabled()) {
                log.trace("Moved {} to {}", sourceFile, destinationFile);
            }
        } catch (IOException ex) {
            throw new FileOperationException("Failed to move file " + sourceFile.getName(), ex);
        }
    }

    private void moveSignatureFile(FileStreamSignature signature) {
        if (downloaderProperties.isKeepSignatures()) {
            Path destination = downloaderProperties.getSignaturesPath().resolve(signature.getNodeAccountIdString());
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
     *
     * @param sigFilesMap signature files grouped by file name
     */
    private void verifySigsAndDownloadDataFiles(Multimap<String, FileStreamSignature> sigFilesMap) {
        Path validPath = downloaderProperties.getValidPath();
        Instant endDate = mirrorProperties.getEndDate();

        for (var sigFilenameIter = sigFilesMap.keySet().iterator(); sigFilenameIter.hasNext(); ) {
            if (ShutdownHelper.isStopping()) {
                return;
            }

            Instant startTime = Instant.now();
            String sigFilename = sigFilenameIter.next();
            Collection<FileStreamSignature> signatures = sigFilesMap.get(sigFilename);
            boolean valid = false;

            try {
                nodeSignatureVerifier.verify(signatures);
            } catch (SignatureVerificationException ex) {
                if (sigFilenameIter.hasNext()) {
                    log.warn("Signature verification failed but still have files in the batch, try to process the " +
                            "next group: {}", ex.getMessage());
                    continue;
                }
                throw ex;
            } finally {
                for (FileStreamSignature signature : signatures) {
                    EntityId nodeAccountId = signature.getNodeAccountId();
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
                    File signedDataFile = downloadSignedDataFile(signature.getFile(), signature
                            .getNodeAccountIdString());
                    if (signedDataFile == null) {
                        continue;
                    }

                    StreamFile streamFile = readStreamFile(signedDataFile);
                    streamFile.setNodeAccountId(signature.getNodeAccountId());

                    verify(streamFile, signature);

                    if (Utility.isStreamFileAfterInstant(sigFilename, endDate)) {
                        downloaderProperties.setEnabled(false);
                        log.warn("Disabled polling after downloading all files <= endDate ({})", endDate);
                        return;
                    }

                    signatures.forEach(this::moveSignatureFile);
                    updateApplicationStatusAndMoveFile(streamFile, validPath, signedDataFile);
                    valid = true;
                    break;
                } catch (HashMismatchException e) {
                    log.warn("Failed to verify data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), signature.getFile().getName(), e);
                } catch (Exception e) {
                    log.error("Error downloading data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), signature.getFile().getName(), e);
                }
            }

            if (!valid) {
                log.error("None of the data files could be verified, signatures: {}", signatures);
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

    /**
     * Verifies the stream file is the next file in the hashchain if it's chained and the hash of the stream file
     * matches the expected hash in the signature.
     *
     * @param streamFile the stream file object
     * @param signature  the signature object corresponding to the stream file
     */
    private void verify(StreamFile streamFile, FileStreamSignature signature) {
        String filename = streamFile.getName();

        if (lastValidDownloadedFileHashKey != null) {
            String expectedPrevHash = applicationStatusRepository.findByStatusCode(lastValidDownloadedFileHashKey);
            Instant verifyHashAfter = downloaderProperties.getMirrorProperties().getVerifyHashAfter();
            if (!verifyHashChain(streamFile.getPreviousHash(), expectedPrevHash, verifyHashAfter, filename)) {
                throw new HashMismatchException(filename, expectedPrevHash, streamFile.getPreviousHash());
            }
        }

        verifyHash(filename, streamFile.getFileHash(), signature.getFileHashAsHex());
        verifyHash(filename, streamFile.getMetadataHash(), signature.getMetadataHashAsHex());
    }

    /**
     * Verifies if the two hashes match.
     *
     * @param filename filename the hash is from
     * @param actual the actual hash
     * @param expected the expected hash
     */
    private void verifyHash(String filename, String actual, String expected) {
        if (!Objects.equals(actual, expected)) {
            throw new HashMismatchException(filename, expected, actual);
        }
    }

    /**
     * Updates last valid downloaded file and last valid downloaded file hash key in database if applicable, saves the
     * stream file to its corresponding database table, and moves the verified data file to the valid folder.
     *
     * @param streamFile       the verified stream file
     * @param validPath        path to the valid folder
     * @param verifiedDataFile the verified data file object
     */
    private void updateApplicationStatusAndMoveFile(StreamFile streamFile, Path validPath, File verifiedDataFile) {
        transactionTemplate.executeWithoutResult(status -> {
            if (lastValidDownloadedFileHashKey != null) {
                applicationStatusRepository
                        .updateStatusValue(lastValidDownloadedFileHashKey, streamFile.getHash());
            }
            applicationStatusRepository.updateStatusValue(lastValidDownloadedFileKey, streamFile.getName());

            saveStreamFileRecord(streamFile);

            // move the file to the valid directory
            File destination = validPath.resolve(verifiedDataFile.getName()).toFile();
            moveFile(verifiedDataFile, destination);
        });
    }

    protected abstract StreamFile readStreamFile(File file);

    protected abstract void saveStreamFileRecord(StreamFile streamFile);

    public abstract void download();
}
