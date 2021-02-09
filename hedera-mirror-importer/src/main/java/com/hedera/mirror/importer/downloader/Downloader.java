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

import static com.hedera.mirror.importer.domain.DigestAlgorithm.SHA384;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.reader.StreamFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

public abstract class Downloader {

    protected final Logger log = LogManager.getLogger(getClass());
    private final S3AsyncClient s3Client;
    private final AddressBookService addressBookService;
    private final ExecutorService signatureDownloadThreadPool; // One per node during the signature download process
    protected final DownloaderProperties downloaderProperties;
    private final MirrorProperties mirrorProperties;
    private final CommonDownloaderProperties commonDownloaderProperties;
    protected final NodeSignatureVerifier nodeSignatureVerifier;
    protected final SignatureFileReader signatureFileReader;
    protected final StreamFileReader<?, ?> streamFileReader;
    protected final StreamFileNotifier streamFileNotifier;
    protected final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    private Optional<StreamFile> lastStreamFile = Optional.empty();

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Counter.Builder signatureVerificationMetric;
    private final Timer.Builder streamVerificationMetric;

    public Downloader(S3AsyncClient s3Client,
                      AddressBookService addressBookService, DownloaderProperties downloaderProperties,
                      MeterRegistry meterRegistry, NodeSignatureVerifier nodeSignatureVerifier,
                      SignatureFileReader signatureFileReader, StreamFileReader<?, ?> streamFileReader,
                      StreamFileNotifier streamFileNotifier,
                      MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        this.s3Client = s3Client;
        this.addressBookService = addressBookService;
        this.downloaderProperties = downloaderProperties;
        this.meterRegistry = meterRegistry;
        this.nodeSignatureVerifier = nodeSignatureVerifier;
        signatureDownloadThreadPool = Executors.newFixedThreadPool(downloaderProperties.getThreads());
        this.signatureFileReader = signatureFileReader;
        this.streamFileReader = streamFileReader;
        this.streamFileNotifier = streamFileNotifier;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
        mirrorProperties = downloaderProperties.getMirrorProperties();
        commonDownloaderProperties = downloaderProperties.getCommon();

        String streamType = downloaderProperties.getStreamType().toString();

        // Metrics
        signatureVerificationMetric = Counter.builder("hedera.mirror.download.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("type", streamType);

        streamVerificationMetric = Timer.builder("hedera.mirror.download.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", streamType);
    }

    public abstract void download();

    protected void downloadNextBatch() {
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
        String lastValidSigFileName = getLastSignature();
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
                            pendingDownloads.add(pendingDownload(s3ObjectKey));
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
                                StreamFileData streamFileData = new StreamFileData(pendingDownload
                                        .getFilename(), pendingDownload.getBytes());
                                FileStreamSignature fileStreamSignature = signatureFileReader.read(streamFileData);
                                fileStreamSignature.setNodeAccountId(nodeAccountId);
                                sigFilesMap.put(fileStreamSignature.getFilename(), fileStreamSignature);
                                count.incrementAndGet();
                            }
                        } catch (InterruptedException ex) {
                            log.warn("Failed downloading {} in {}", pendingDownload.getS3key(),
                                    pendingDownload.getStopwatch(), ex);
                            Thread.currentThread().interrupt();
                        } catch (Exception ex) {
                            log.warn("Failed to parse signature file {}: {}", pendingDownload.getS3key(), ex);
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

    /**
     * Returns the last signature file name that was successfully verified. On startup, this will be the last file
     * successfully imported into the database since all files are downloaded into memory will have been discarded.
     * Unless startDate or demo network is set, then those take precedence. Since 'foo.rcd' is lexicographically before
     * 'foo.rcd_sig' we have to start listing from after 'foo.rcd_sig'.
     *
     * @return last signature file name
     */
    private String getLastSignature() {
        return lastStreamFile
                .map(StreamFile::getName)
                .or(() -> {
                    StreamType streamType = downloaderProperties.getStreamType();
                    Instant startDate = mirrorDateRangePropertiesProcessor.getEffectiveStartDate(downloaderProperties);
                    return Optional.of(Utility.getStreamFilenameFromInstant(streamType, startDate));
                })
                .map(name -> name + "_sig")
                .orElse("");
    }

    /**
     * Returns a PendingDownload for which the caller can waitForCompletion() to wait for the download to complete. This
     * either queues or begins the download (depending on the AWS TransferManager).
     *
     * @param s3ObjectKey
     * @return
     */
    private PendingDownload pendingDownload(String s3ObjectKey) {
        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3ObjectKey)
                .requestPayer(RequestPayer.REQUESTER)
                .build();
        var future = s3Client.getObject(request, AsyncResponseTransformer.toBytes());
        return new PendingDownload(future, s3ObjectKey);
    }

    private void moveSignatureFile(FileStreamSignature signature) {
        if (downloaderProperties.isKeepSignatures()) {
            Path destination = downloaderProperties.getSignaturesPath().resolve(signature.getNodeAccountIdString());
            Utility.archiveFile(signature.getFilename(), signature.getBytes(), destination);
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
                    PendingDownload pendingDownload = downloadSignedDataFile(signature);
                    if (!pendingDownload.waitForCompletion()) {
                        continue;
                    }

                    StreamFileData streamFileData = new StreamFileData(pendingDownload.getFilename(),
                            pendingDownload.getBytes());
                    StreamFile streamFile = streamFileReader.read(streamFileData);
                    streamFile.setNodeAccountId(signature.getNodeAccountId());

                    verify(streamFile, signature);

                    if (Utility.isStreamFileAfterInstant(sigFilename, endDate)) {
                        downloaderProperties.setEnabled(false);
                        log.warn("Disabled polling after downloading all files <= endDate ({})", endDate);
                        return;
                    }

                    signatures.forEach(this::moveSignatureFile);
                    streamFileNotifier.verified(streamFile);
                    lastStreamFile = Optional.of(streamFile);
                    onVerified(streamFile);
                    valid = true;
                    break;
                } catch (HashMismatchException e) {
                    log.warn("Failed to verify data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), signature.getFilename(), e);
                } catch (Exception e) {
                    log.error("Error downloading data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), signature.getFilename(), e);
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

    private PendingDownload downloadSignedDataFile(FileStreamSignature fileStreamSignature) throws Exception {
        String fileName = fileStreamSignature.getFilename().replace("_sig", "");
        String s3Prefix = downloaderProperties.getPrefix();
        String nodeAccountId = fileStreamSignature.getNodeAccountIdString();
        String s3ObjectKey = s3Prefix + nodeAccountId + "/" + fileName;

        return pendingDownload(s3ObjectKey);
    }

    protected void onVerified(StreamFile streamFile) {
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
        String expectedPrevHash = lastStreamFile.map(StreamFile::getHash).orElse(null);

        if (!verifyHashChain(streamFile, expectedPrevHash)) {
            throw new HashMismatchException(filename, expectedPrevHash, streamFile.getPreviousHash());
        }

        verifyHash(filename, streamFile.getFileHash(), signature.getFileHashAsHex());
        verifyHash(filename, streamFile.getMetadataHash(), signature.getMetadataHashAsHex());
    }

    /**
     * Verifies if the two hashes match.
     *
     * @param filename filename the hash is from
     * @param actual   the actual hash
     * @param expected the expected hash
     */
    private void verifyHash(String filename, String actual, String expected) {
        if (!Objects.equals(actual, expected)) {
            throw new HashMismatchException(filename, expected, actual);
        }
    }

    private boolean verifyHashChain(StreamFile streamFile, String expectedPreviousHash) {
        if (!streamFile.getType().isChained()) {
            return true;
        }

        Instant verifyHashAfter = downloaderProperties.getMirrorProperties().getVerifyHashAfter();
        var fileInstant = Utility.getInstantFromFilename(streamFile.getName());

        if (!verifyHashAfter.isBefore(fileInstant)) {
            return true;
        }

        if (SHA384.isHashEmpty(expectedPreviousHash)) {
            log.warn("Previous hash not available");
            return true;
        }

        return streamFile.getPreviousHash().contentEquals(expectedPreviousHash);
    }
}
