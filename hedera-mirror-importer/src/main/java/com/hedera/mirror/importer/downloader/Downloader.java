/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.downloader;

import static com.hedera.mirror.common.domain.DigestAlgorithm.SHA_384;
import static com.hedera.mirror.importer.domain.StreamFileSignature.SignatureStatus;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.downloader.provider.TransientProviderException;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.SignatureVerificationException;
import com.hedera.mirror.importer.reader.StreamFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Downloader<T extends StreamFile<I>, I extends StreamItem> {

    public static final String STREAM_CLOSE_LATENCY_METRIC_NAME = "hedera.mirror.stream.close.latency";

    private static final String HASH_TYPE_FILE = "File";
    private static final String HASH_TYPE_METADATA = "Metadata";
    private static final String HASH_TYPE_RUNNING = "Running";

    private static final Comparator<StreamFileSignature> STREAM_FILE_SIGNATURE_COMPARATOR = (left, right) -> {
        if (Objects.equals(left, right)) {
            // Ensures values are unique when used in a Set
            return 0;
        }

        // The arbitrary ordering compares objects by identity, thus when used in a sorted collection, it gives a random
        // order of the StreamFileSignatures w.r.t the nodes
        return Ordering.arbitrary().compare(left, right);
    };

    protected final Logger log = LogManager.getLogger(getClass());
    protected final DownloaderProperties downloaderProperties;
    protected final NodeSignatureVerifier nodeSignatureVerifier;
    protected final SignatureFileReader signatureFileReader;
    protected final StreamFileProvider streamFileProvider;
    protected final StreamFileReader<T, ?> streamFileReader;
    protected final StreamFileNotifier streamFileNotifier;
    protected final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;
    protected final AtomicReference<Optional<T>> lastStreamFile = new AtomicReference<>(Optional.empty());
    private final ConsensusNodeService consensusNodeService;
    private final ExecutorService signatureDownloadThreadPool; // One per node during the signature download process
    private final MirrorProperties mirrorProperties;
    private final StreamType streamType;
    // Metrics
    private final MeterRegistry meterRegistry;
    private final Map<Long, Counter> nodeSignatureStatusMetricMap = new ConcurrentHashMap<>();
    private final Timer cloudStorageLatencyMetric;
    private final Timer downloadLatencyMetric;
    private final Timer streamCloseMetric;
    private final Timer.Builder streamVerificationMetric;

    @SuppressWarnings({"java:S107", "java:S3740"})
    protected Downloader(
            ConsensusNodeService consensusNodeService,
            DownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
            NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            StreamFileReader<T, ?> streamFileReader) {
        this.consensusNodeService = consensusNodeService;
        this.downloaderProperties = downloaderProperties;
        this.meterRegistry = meterRegistry;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
        this.nodeSignatureVerifier = nodeSignatureVerifier;
        var threads = downloaderProperties.getCommon().getThreads();
        this.signatureDownloadThreadPool = Executors.newFixedThreadPool(threads);
        this.signatureFileReader = signatureFileReader;
        this.streamFileProvider = streamFileProvider;
        this.streamFileReader = streamFileReader;
        this.streamFileNotifier = streamFileNotifier;
        Runtime.getRuntime().addShutdownHook(new Thread(signatureDownloadThreadPool::shutdown));
        mirrorProperties = downloaderProperties.getMirrorProperties();

        streamType = downloaderProperties.getStreamType();

        // Metrics
        cloudStorageLatencyMetric = Timer.builder("hedera.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was created in the cloud storage provider")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        downloadLatencyMetric = Timer.builder("hedera.mirror.download.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was downloaded and verified")
                .tag("type", streamType.toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder(STREAM_CLOSE_LATENCY_METRIC_NAME)
                .description("The difference between the consensus start of the current and the last stream file")
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

        try {
            var sigFilesMap = downloadAndParseSigFiles();

            // Following is a cost optimization to not unnecessarily list the public demo bucket once complete
            if (sigFilesMap.isEmpty()
                    && MirrorProperties.HederaNetwork.DEMO.equalsIgnoreCase(mirrorProperties.getNetwork())) {
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
     * Sets the index of the streamFile to the last index plus 1, or 0 if it's the first stream file.
     *
     * @param streamFile the stream file object
     */
    protected void setStreamFileIndex(T streamFile) {
        long index = lastStreamFile
                .get()
                .map(StreamFile::getIndex)
                .map(v -> v + 1)
                .or(() -> Optional.ofNullable(mirrorProperties.getStartBlockNumber()))
                .orElse(0L);
        streamFile.setIndex(index);
    }

    Multimap<StreamFilename, StreamFileSignature> getStreamFileSignatureMultiMap() {
        // The custom comparator ensures there is no duplicate key-value pairs and randomly sorts the values associated
        // with the same key
        return TreeMultimap.create(Ordering.natural(), STREAM_FILE_SIGNATURE_COMPARATOR);
    }

    /**
     * Download and parse all signature files with a timestamp later than the last valid file. Put signature files into
     * a multi-map sorted and grouped by the timestamp.
     *
     * @return a multi-map of signature file objects from different nodes, grouped by filename
     */
    private Multimap<StreamFilename, StreamFileSignature> downloadAndParseSigFiles() throws InterruptedException {
        var startAfterFilename = getStartAfterFilename();
        var sigFilesMap = Multimaps.synchronizedMultimap(getStreamFileSignatureMultiMap());

        var nodes = consensusNodeService.getNodes();
        var tasks = new ArrayList<Callable<Object>>(nodes.size());
        var totalDownloads = new AtomicInteger();
        log.debug("Asking for new signature files created after file: {}", startAfterFilename);

        /*
         * For each node, create a thread that will make requests as many times as necessary to
         * start maxDownloads download operations.
         */
        for (var node : nodes) {
            tasks.add(Executors.callable(() -> {
                var stopwatch = Stopwatch.createStarted();

                try {
                    var count = streamFileProvider
                            .list(node, startAfterFilename)
                            .doOnNext(s -> {
                                try {
                                    var streamFileSignature = signatureFileReader.read(s);
                                    streamFileSignature.setNode(node);
                                    streamFileSignature.setStreamType(streamType);
                                    sigFilesMap.put(streamFileSignature.getFilename(), streamFileSignature);
                                } catch (Exception ex) {
                                    log.warn("Failed to parse signature file {}: {}", "", ex);
                                }
                            })
                            .count()
                            .block();

                    if (count > 0) {
                        totalDownloads.addAndGet(count.intValue());
                        log.info("Downloaded {} signatures for node {} in {}", count, node, stopwatch);
                    }
                } catch (Exception e) {
                    log.error("Error downloading signature files for node {} after {}", node, stopwatch, e);
                }
            }));
        }

        // Wait for all tasks to complete.
        // invokeAll() does return Futures, but it waits for all to complete (so they're returned in a completed state).
        Stopwatch stopwatch = Stopwatch.createStarted();
        long timeoutMillis = downloaderProperties.getCommon().getTimeout().toMillis();
        signatureDownloadThreadPool.invokeAll(tasks, timeoutMillis, TimeUnit.MILLISECONDS);

        if (totalDownloads.get() > 0) {
            var rate = (int) (1000000.0 * totalDownloads.get() / stopwatch.elapsed(TimeUnit.MICROSECONDS));
            log.info("Downloaded {} signatures in {} ({}/s)", totalDownloads, stopwatch, rate);
        } else {
            log.info(
                    "No new signature files to download after file: {}. Retrying in {} s",
                    startAfterFilename,
                    downloaderProperties.getFrequency().toMillis() / 1_000f);
        }

        return sigFilesMap;
    }

    /**
     * Returns the file name in between the last signature file name that was successfully verified and the next stream
     * file to process in the cloud bucket. On startup, the last signature file name will be the last file successfully
     * imported into the database since all files are downloaded into memory will have been discarded. If startDate or
     * demo network is set, those take precedence.
     *
     * @return filename lexically after the last signature file and before the next stream file
     */
    private StreamFilename getStartAfterFilename() {
        return lastStreamFile
                .get()
                .or(() -> {
                    Optional<T> streamFile = mirrorDateRangePropertiesProcessor.getLastStreamFile(streamType);
                    lastStreamFile.compareAndSet(Optional.empty(), streamFile);
                    return streamFile;
                })
                .map(StreamFile::getName)
                .map(StreamFilename::from)
                .orElse(StreamFilename.EPOCH);
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
    @SuppressWarnings("java:S135")
    private void verifySigsAndDownloadDataFiles(Multimap<StreamFilename, StreamFileSignature> sigFilesMap) {
        var nodeIds = consensusNodeService.getNodes().stream()
                .map(ConsensusNode::getNodeId)
                .collect(Collectors.toSet());

        for (var sigFilenameIter = sigFilesMap.keySet().iterator(); sigFilenameIter.hasNext(); ) {
            if (ShutdownHelper.isStopping()) {
                return;
            }

            Instant startTime = Instant.now();
            var sigFilename = sigFilenameIter.next();
            var signatures = sigFilesMap.get(sigFilename);

            try {
                nodeSignatureVerifier.verify(signatures);

                var consensusCount = signatures.stream()
                        .filter(s -> s.getStatus() == StreamFileSignature.SignatureStatus.CONSENSUS_REACHED)
                        .count();

                if (consensusCount == nodeIds.size()) {
                    log.debug("Verified signature file {} reached consensus", sigFilename);
                } else if (consensusCount > 0) {
                    log.warn(
                            "Verified signature file {} reached consensus but with some errors: {}",
                            sigFilename,
                            statusMap(signatures, nodeIds));
                }
            } catch (SignatureVerificationException ex) {
                var statusMapMessage = statusMap(signatures, nodeIds);
                if (sigFilenameIter.hasNext()) {
                    log.warn("{}. Trying next group: {}", ex.getMessage(), statusMapMessage);
                    continue;
                }

                throw new SignatureVerificationException(ex.getMessage() + ": " + statusMapMessage);
            }

            boolean valid = verifySignatures(signatures);
            if (!valid) {
                log.error("None of the data files could be verified, signatures: {}", signatures);
            }

            streamVerificationMetric
                    .tag("success", String.valueOf(valid))
                    .register(meterRegistry)
                    .record(Duration.between(startTime, Instant.now()));
        }
    }

    private boolean verifySignatures(Collection<StreamFileSignature> signatures) {
        Instant endDate = mirrorProperties.getEndDate();

        for (var signature : signatures) {
            // Ignore signatures that didn't validate or weren't in the majority
            if (signature.getStatus() != StreamFileSignature.SignatureStatus.CONSENSUS_REACHED) {
                continue;
            }

            var nodeId = signature.getNode().getNodeId();

            try {
                var dataFilename = signature.getDataFilename();
                var node = signature.getNode();
                var streamFileData = streamFileProvider.get(node, dataFilename).block();
                T streamFile = streamFileReader.read(streamFileData);
                streamFile.setNodeId(nodeId);

                verify(streamFile, signature);

                if (downloaderProperties.isWriteFiles()) {
                    Utility.archiveFile(
                            streamFileData.getStreamFilename(),
                            streamFile.getBytes(),
                            downloaderProperties.getStreamPath());
                }

                if (downloaderProperties.isWriteSignatures()) {
                    var destination = downloaderProperties.getStreamPath();
                    signatures.forEach(s -> Utility.archiveFile(s.getFilename(), s.getBytes(), destination));
                }

                if (!downloaderProperties.isPersistBytes()) {
                    streamFile.setBytes(null);
                }

                if (dataFilename.getInstant().isAfter(endDate)) {
                    downloaderProperties.setEnabled(false);
                    log.warn("Disabled polling after downloading all files <= endDate ({})", endDate);
                    return false;
                }

                onVerified(streamFileData, streamFile, node);
                return true;
            } catch (HashMismatchException | TransientProviderException e) {
                log.warn(
                        "Failed processing signature from node {} corresponding to {}. Will retry another node: {}",
                        nodeId,
                        signature.getFilename(),
                        e.getMessage());
            } catch (Exception e) {
                log.error(
                        "Error downloading data file from node {} corresponding to {}. Will retry another node",
                        nodeId,
                        signature.getFilename(),
                        e);
            }
        }

        return false;
    }

    @SuppressWarnings({"unchecked", "java:S1172"}) // Unused Parameter (node) required by subclass implementations
    protected void onVerified(StreamFileData streamFileData, T streamFile, ConsensusNode node) {
        setStreamFileIndex(streamFile);
        streamFileNotifier.verified(streamFile);

        lastStreamFile.get().ifPresent(last -> {
            long latency = streamFile.getConsensusStart() - last.getConsensusStart();
            streamCloseMetric.record(latency, TimeUnit.NANOSECONDS);
        });

        Instant cloudStorageTime = streamFileData.getLastModified();
        Instant consensusEnd = Instant.ofEpochSecond(0, streamFile.getConsensusEnd());
        cloudStorageLatencyMetric.record(Duration.between(consensusEnd, cloudStorageTime));
        downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));

        // Cache a copy of the streamFile with bytes and items set to null so as not to keep them in memory
        var copy = (T) streamFile.copy();
        copy.setBytes(null);
        copy.setItems(null);
        lastStreamFile.set(Optional.of(copy));
    }

    /**
     * Verifies the stream file is the next file in the hashchain if it's chained and the hash of the stream file
     * matches the expected hash in the signature.
     *
     * @param streamFile the stream file object
     * @param signature  the signature object corresponding to the stream file
     */
    private void verify(T streamFile, StreamFileSignature signature) {
        String filename = streamFile.getName();
        String expectedPrevHash = lastStreamFile.get().map(StreamFile::getHash).orElse(null);

        if (!verifyHashChain(streamFile, expectedPrevHash)) {
            throw new HashMismatchException(
                    filename, expectedPrevHash, streamFile.getPreviousHash(), HASH_TYPE_RUNNING);
        }

        verifyHash(filename, streamFile.getFileHash(), signature.getFileHashAsHex(), HASH_TYPE_FILE);
        verifyHash(filename, streamFile.getMetadataHash(), signature.getMetadataHashAsHex(), HASH_TYPE_METADATA);
    }

    /**
     * Verifies if the two hashes match.
     *
     * @param filename filename the hash is from
     * @param actual   the actual hash
     * @param expected the expected hash
     */
    private void verifyHash(String filename, String actual, String expected, String hashType) {
        if (!Objects.equals(actual, expected)) {
            throw new HashMismatchException(filename, expected, actual, hashType);
        }
    }

    boolean verifyHashChain(T streamFile, String expectedPreviousHash) {
        if (!streamFile.getType().isChained()) {
            return true;
        }

        Instant verifyHashAfter = downloaderProperties.getMirrorProperties().getVerifyHashAfter();
        Instant fileInstant = Instant.ofEpochSecond(0, streamFile.getConsensusStart());

        if (!verifyHashAfter.isBefore(fileInstant)) {
            return true;
        }

        if (SHA_384.isHashEmpty(expectedPreviousHash)) {
            log.warn("Previous hash not available");
            return true;
        }

        return streamFile.getPreviousHash().contentEquals(expectedPreviousHash);
    }

    private Map<SignatureStatus, Collection<Long>> statusMap(
            Collection<StreamFileSignature> signatures, Set<Long> nodeIds) {
        Map<SignatureStatus, Collection<Long>> statusMap = signatures.stream()
                .collect(Collectors.groupingBy(
                        StreamFileSignature::getStatus,
                        Collectors.mapping(s -> s.getNode().getNodeId(), Collectors.toCollection(TreeSet::new))));

        var seenNodes = signatures.stream().map(s -> s.getNode().getNodeId()).collect(Collectors.toSet());
        var missingNodes = new TreeSet<>(Sets.difference(nodeIds, seenNodes));
        statusMap.put(SignatureStatus.NOT_FOUND, missingNodes);

        String signatureStreamType = signatures.stream()
                .map(StreamFileSignature::getStreamType)
                .map(StreamType::toString)
                .findFirst()
                .orElse("UNKNOWN");

        for (var entry : statusMap.entrySet()) {
            entry.getValue().forEach(nodeId -> {
                Counter counter = nodeSignatureStatusMetricMap.computeIfAbsent(
                        nodeId, n -> newStatusMetric(nodeId, signatureStreamType, entry.getKey()));
                counter.increment();
            });
        }

        // remove CONSENSUS_REACHED for logging purposes
        statusMap.remove(SignatureStatus.CONSENSUS_REACHED);
        return statusMap;
    }

    private Counter newStatusMetric(Long nodeId, String streamType, SignatureStatus status) {
        return Counter.builder("hedera.mirror.download.signature.verification")
                .description("The number of signatures verified from a particular node")
                .tag("node", nodeId.toString())
                .tag("type", streamType)
                .tag("status", status.toString())
                .register(meterRegistry);
    }
}
