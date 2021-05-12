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
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
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
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.reader.StreamFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

public abstract class Downloader<T extends StreamFile> {

    protected final Logger log = LogManager.getLogger(getClass());
    private final S3AsyncClient s3Client;
    private final AddressBookService addressBookService;
    private final ExecutorService signatureDownloadThreadPool; // One per node during the signature download process
    protected final DownloaderProperties downloaderProperties;
    private final MirrorProperties mirrorProperties;
    private final CommonDownloaderProperties commonDownloaderProperties;
    protected final NodeSignatureVerifier nodeSignatureVerifier;
    protected final SignatureFileReader signatureFileReader;
    protected final StreamFileReader<T, ?> streamFileReader;
    protected final StreamFileNotifier streamFileNotifier;
    protected final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;
    private final StreamType streamType;

    protected final AtomicReference<Optional<T>> lastStreamFile = new AtomicReference<>(Optional.empty());

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Timer cloudStorageLatencyMetric;
    private final Timer downloadLatencyMetric;
    private final Timer streamCloseMetric;
    private final Timer.Builder streamVerificationMetric;

    protected Downloader(S3AsyncClient s3Client,
                         AddressBookService addressBookService, DownloaderProperties downloaderProperties,
                         MeterRegistry meterRegistry, NodeSignatureVerifier nodeSignatureVerifier,
                         SignatureFileReader signatureFileReader, StreamFileReader<T, ?> streamFileReader,
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

        streamType = downloaderProperties.getStreamType();

        // Metrics
        cloudStorageLatencyMetric = Timer.builder("hedera.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file " +
                        "and the time at which the file was created in the cloud storage provider")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        downloadLatencyMetric = Timer.builder("hedera.mirror.download.latency")
                .description("The difference in time between the consensus time of the last transaction in the file " +
                        "and the time at which the file was downloaded and verified")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder("hedera.mirror.stream.close.latency")
                .description("The difference between the consensus time of the last and first transaction in the " +
                        "stream file")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamVerificationMetric = Timer.builder("hedera.mirror.download.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", streamType.toString());
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
        } catch (InterruptedException e) {
            log.error("Error downloading files", e);
            Thread.currentThread().interrupt();
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
        String startAfterFilename = getStartAfterFilename();
        Multimap<String, FileStreamSignature> sigFilesMap = Multimaps
                .synchronizedSortedSetMultimap(TreeMultimap.create());

        Set<EntityId> nodeAccountIds = addressBook.getNodeSet();
        List<Callable<Object>> tasks = new ArrayList<>(nodeAccountIds.size());
        AtomicInteger totalDownloads = new AtomicInteger();
        log.info("Downloading signature files created after file: {}", startAfterFilename);

        /*
         * For each node, create a thread that will make S3 ListObject requests as many times as necessary to
         * start maxDownloads download operations.
         */
        for (EntityId nodeAccountId : nodeAccountIds) {
            tasks.add(Executors.callable(() -> {
                String nodeAccountIdStr = nodeAccountId.entityIdToString();
                Stopwatch stopwatch = Stopwatch.createStarted();

                try {
                    List<S3Object> s3Objects = listFiles(startAfterFilename, nodeAccountIdStr);
                    List<PendingDownload> pendingDownloads = downloadSignatureFiles(nodeAccountIdStr, s3Objects);
                    AtomicInteger count = new AtomicInteger();
                    pendingDownloads.forEach(pendingDownload -> {
                        try {
                            parseSignatureFile(pendingDownload, nodeAccountId)
                                    .ifPresent(fileStreamSignature -> {
                                        sigFilesMap.put(fileStreamSignature.getFilename(), fileStreamSignature);
                                        count.incrementAndGet();
                                        totalDownloads.incrementAndGet();
                                    });
                        } catch (InterruptedException ex) {
                            log.warn("Failed downloading {} in {}", pendingDownload.getS3key(),
                                    pendingDownload.getStopwatch(), ex);
                            Thread.currentThread().interrupt();
                        } catch (Exception ex) {
                            log.warn("Failed to parse signature file {}: {}", pendingDownload.getS3key(), ex);
                        }
                    });

                    if (count.get() > 0) {
                        log.info("Downloaded {} signatures for node {} in {}", count.get(), nodeAccountIdStr,
                                stopwatch);
                    }
                } catch (InterruptedException e) {
                    log.error("Error downloading signature files for node {} after {}", nodeAccountIdStr, stopwatch, e);
                    Thread.currentThread().interrupt();
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

    private List<S3Object> listFiles(String lastFilename, String nodeAccountId) throws ExecutionException,
            InterruptedException {
        // batchSize (number of items we plan do download in a single batch) times 2 for file + sig.
        int listSize = (downloaderProperties.getBatchSize() * 2);
        String s3Prefix = getS3Prefix(nodeAccountId);
        // Not using ListObjectsV2Request because it does not work with GCP.
        ListObjectsRequest listRequest = ListObjectsRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .prefix(s3Prefix)
                .delimiter("/")
                .marker(s3Prefix + lastFilename)
                .maxKeys(listSize)
                .requestPayer(RequestPayer.REQUESTER)
                .build();
        return s3Client.listObjects(listRequest).get().contents();
    }

    private List<PendingDownload> downloadSignatureFiles(String nodeAccountId, List<S3Object> s3Objects) {
        // group the signature filenames by its instant
        Map<Instant, Optional<StreamFilename>> signatureFilenamesByInstant = s3Objects.stream()
                .map(S3Object::key)
                .map(key -> key.substring(key.lastIndexOf('/') + 1))
                .map(filename -> {
                    try {
                        return new StreamFilename(filename);
                    } catch (InvalidStreamFileException e) {
                        log.error(e);
                        return null;
                    }
                })
                .filter(s -> s != null && s.getFileType() == SIGNATURE)
                .collect(groupingBy(StreamFilename::getInstant, maxBy(StreamFilename.EXTENSION_COMPARATOR)));

        String s3Prefix = getS3Prefix(nodeAccountId);
        return signatureFilenamesByInstant.values()
                .stream()
                .filter(Optional::isPresent)
                .map(s -> pendingDownload(s.get(), s3Prefix))
                .collect(Collectors.toList());
    }

    private Optional<FileStreamSignature> parseSignatureFile(PendingDownload pendingDownload, EntityId nodeAccountId) throws InterruptedException, ExecutionException {
        String s3Key = pendingDownload.getS3key();
        Stopwatch stopwatch = pendingDownload.getStopwatch();

        if (!pendingDownload.waitForCompletion()) {
            log.warn("Failed downloading {} in {}", s3Key, stopwatch);
            return Optional.empty();
        }

        StreamFilename streamFilename = pendingDownload.getStreamFilename();
        StreamFileData streamFileData = new StreamFileData(streamFilename, pendingDownload.getBytes());
        FileStreamSignature fileStreamSignature = signatureFileReader.read(streamFileData);
        fileStreamSignature.setNodeAccountId(nodeAccountId);
        fileStreamSignature.setStreamType(streamType);
        return Optional.of(fileStreamSignature);
    }

    /**
     * Returns the file name in between the last signature file name that was successfully verified and the next stream
     * file to process in the cloud bucket. On startup, the last signature file name will be the last file successfully
     * imported into the database since all files are downloaded into memory will have been discarded. If startDate or
     * demo network is set, those take precedence.
     *
     * @return filename lexically after the last signature file and before the next stream file
     */
    private String getStartAfterFilename() {
        return lastStreamFile.get()
                .or(() -> {
                    Optional<T> streamFile = mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType);
                    lastStreamFile.compareAndSet(Optional.empty(), streamFile);
                    return streamFile;
                })
                .map(StreamFile::getName)
                .map(StreamFilename::new)
                .map(StreamFilename::getFilenameAfter)
                .orElse("");
    }

    /**
     * Returns a PendingDownload for which the caller can waitForCompletion() to wait for the download to complete. This
     * either queues or begins the download (depending on the AWS TransferManager).
     *
     * @param streamFilename
     * @param s3Prefix
     * @return
     */
    private PendingDownload pendingDownload(StreamFilename streamFilename, String s3Prefix) {
        String s3Key = s3Prefix + streamFilename.getFilename();
        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();
        var future = s3Client.getObject(request, AsyncResponseTransformer.toBytes());
        return new PendingDownload(future, streamFilename, s3Key);
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
     * @param sigFilesMap signature files grouped by filename
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

                    StreamFilename dataFilename = pendingDownload.getStreamFilename();
                    StreamFileData streamFileData = new StreamFileData(dataFilename, pendingDownload.getBytes());
                    T streamFile = streamFileReader.read(streamFileData);
                    streamFile.setNodeAccountId(signature.getNodeAccountId());

                    verify(streamFile, signature);

                    if (dataFilename.getInstant().isAfter(endDate)) {
                        downloaderProperties.setEnabled(false);
                        log.warn("Disabled polling after downloading all files <= endDate ({})", endDate);
                        return;
                    }

                    signatures.forEach(this::moveSignatureFile);
                    onVerified(pendingDownload, streamFile);
                    valid = true;
                    break;
                } catch (HashMismatchException e) {
                    log.warn("Failed to verify data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), sigFilename, e);
                } catch (InterruptedException e) {
                    log.warn("Failed to download data file from node {} corresponding to {}",
                            signature.getNodeAccountIdString(), sigFilename, e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Error downloading data file from node {} corresponding to {}. Will retry another node",
                            signature.getNodeAccountIdString(), sigFilename, e);
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

    private PendingDownload downloadSignedDataFile(FileStreamSignature fileStreamSignature) {
        String filename = fileStreamSignature.getFilename().replace(StreamType.SIGNATURE_SUFFIX, "");
        String nodeAccountId = fileStreamSignature.getNodeAccountIdString();
        return pendingDownload(new StreamFilename(filename), getS3Prefix(nodeAccountId));
    }

    private String getS3Prefix(String nodeAccountId) {
        return downloaderProperties.getPrefix() + nodeAccountId + "/";
    }

    protected void onVerified(PendingDownload pendingDownload, T streamFile) throws ExecutionException,
            InterruptedException {
        setStreamFileIndex(streamFile);
        streamFileNotifier.verified(streamFile);
        lastStreamFile.set(Optional.of(streamFile));

        long streamClose = streamFile.getConsensusEnd() - streamFile.getConsensusStart();
        if (streamClose > 0) {
            streamCloseMetric.record(streamClose, TimeUnit.NANOSECONDS);
        }

        Instant cloudStorageTime = pendingDownload.getObjectResponse().lastModified();
        Instant consensusEnd = Instant.ofEpochSecond(0, streamFile.getConsensusEnd());
        cloudStorageLatencyMetric.record(Duration.between(consensusEnd, cloudStorageTime));
        downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));
    }

    /**
     * Sets the index of the streamFile to the last index plus 1, or 0 if it's the first stream file.
     *
     * @param streamFile the stream file object
     */
    private void setStreamFileIndex(StreamFile streamFile) {
        long index = lastStreamFile.get()
                .map(StreamFile::getIndex)
                .map(v -> v + 1)
                .orElse(0L);
        streamFile.setIndex(index);
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
        String expectedPrevHash = lastStreamFile.get().map(StreamFile::getHash).orElse(null);

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

    boolean verifyHashChain(StreamFile streamFile, String expectedPreviousHash) {
        if (!streamFile.getType().isChained()) {
            return true;
        }

        Instant verifyHashAfter = downloaderProperties.getMirrorProperties().getVerifyHashAfter();
        Instant fileInstant = Instant.ofEpochSecond(0, streamFile.getConsensusStart());

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
