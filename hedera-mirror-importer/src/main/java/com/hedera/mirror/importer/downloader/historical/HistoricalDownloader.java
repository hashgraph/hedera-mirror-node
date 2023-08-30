/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.historical;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider.GetObjectResponseWithKey;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.leader.Leader;
import jakarta.inject.Named;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.Value;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.model.S3Object;

@Named
@CustomLog
public class HistoricalDownloader {

    public static final String SEPARATOR = "/";

    private final ConsensusNodeService consensusNodeService;
    private final Path downloadPath;
    private final DownloaderProperties downloaderProperties;
    private final ExecutorService executorService = Executors.newFixedThreadPool(100);
    private final StreamFileProvider streamFileProvider;
    private final StreamType streamType;
    private final ConcurrentMap<String, FileSpecificInfo> downloadsInfoMap = new ConcurrentSkipListMap<>();
    private final AtomicReference<ConsensusNodeInfo> nodeInfoRef = new AtomicReference<>();
    private final int downloadConcurrency = 1; // Debug, make a property

    public HistoricalDownloader(
            ConsensusNodeService consensusNodeService,
            HistoricalDownloaderProperties downloaderProperties,
            StreamFileProvider streamFileProvider) {

        this.consensusNodeService = consensusNodeService;
        this.downloaderProperties = downloaderProperties;
        this.streamFileProvider = streamFileProvider;
        this.streamType = downloaderProperties.getStreamType();
        this.downloadPath = downloaderProperties.getMirrorProperties().getDownloadPath();
    }

    private static String s3Basename(String s3Key) {
        var lastSeparatorIndex = s3Key.lastIndexOf(SEPARATOR);
        return lastSeparatorIndex < 0 ? s3Key : s3Key.substring(lastSeparatorIndex + 1);
    }

    private static String s3Prefix(String s3Key) {
        var lastSeparatorIndex = s3Key.lastIndexOf(SEPARATOR);
        return lastSeparatorIndex < 0 ? null : s3Key.substring(0, lastSeparatorIndex + 1);
    }

    private static boolean isSignatureFileObject(S3Object s3Object) {
        return isSignatureFileName(s3Object.key());
    }

    private static boolean isSignatureFileName(String filename) {
        return filename.endsWith(StreamType.SIGNATURE_SUFFIX);
    }

    @Leader
    // Run once
    @Scheduled(initialDelay = 1000 * 5, fixedDelay = Long.MAX_VALUE)
    public void downloadAll() {
        log.info("Starting download from epoc for stream type {}", streamType);
        downloadAll(StreamFilename.EPOCH);
    }

    public void downloadAll(StreamFilename startFilename) {

        /* NOTE: For first part (6413), the address book will not change while downloading since the data files
         * are not being imported.
         *
         * NOTE: AddressBookServiceImpl.getNodes() is @Cacheable. When an address book update is detected
         * (update to file ID 102), AddressBookServiceImpl.update() evicts all cache entries.
         *
         * Starting with the initial address book (network specific genesis classpath resource or property), the address
         * book changes over time as file update transactions are processed. It seems that as the address book
         * changes, the number of signature/stream files being downloaded should change so that sufficient
         * numbers are present in the file system for when the Downloader processes them later.
         *
         * Currently, the various stream file type downloaders do choose random nodes and calculate 1/3 state for
         * each invocation of downloadNextBatch(), which is pretty frequent, but certainly not each signature
         * file listed. Maybe utilize an epoch minute or hour cache key or something?
         */
        var nodeInfo = partialCollection(consensusNodeService.getNodes());
        //        var nodeInfo = partialCollection(
        //                List.of(consensusNodeService.getNodes().iterator().next())); // TODO Single node for initial
        // debug
        nodeInfoRef.set(nodeInfo);

        List<CompletableFuture<Long>> downloaders = new ArrayList<>(nodeInfo.nextIndex);
        for (int nodeIdx = 0; nodeIdx < nodeInfo.nextIndex; nodeIdx++) {
            var node = nodeInfo.nodes().get(nodeIdx);

            downloaders.add(CompletableFuture.supplyAsync(
                    () -> {
                        log.info("Downloading signatures for node {}", node);

                        var stopwatch = Stopwatch.createStarted();
                        AtomicReference<S3Object> previousDataFileObjectRef = new AtomicReference<>();

                        /*
                         * The node specific directory hierarchy within the S3 bucket contains both the signature and
                         * stream data files. A common timestamp forms the first part of the pair of signature and
                         * data files. In the listing of objects returned from S3, the data file precedes the
                         * signature file.
                         *
                         * 2019-10-11T13_32_41.443132Z.rcd
                         * 2019-10-11T13_32_41.443132Z.rcd_sig
                         *  ...
                         * 2023-05-11T00_00_00.296936002Z.rcd.gz
                         * 2023-05-11T00_00_00.296936002Z.rcd_sig
                         *  ...
                         * 2019-10-11T15_30_00.026419Z_Balances.csv
                         * 2019-10-11T15_30_00.026419Z_Balances.csv_sig
                         *
                         * The signature file protobufs are not processed, so version and compressor information
                         * is not explicitly known. So, the file preceding a signature file is the assumed to be
                         * the related data file. It is possible that a consensus node, due to some malfunction
                         * perhaps, failed to upload the data file. In that case the mapped FileSpecificInfo will
                         * contain a null reference which will get set later for another consensus node that
                         * did provide the data file.
                         *
                         * NOTE: Need to integrate with sidecar files.
                         */
                        try {
                            var count = streamFileProvider
                                    .listAllPaginated(node, startFilename)
                                    .log()
                                    .doOnNext(s3Object -> {
                                        if (isSignatureFileObject(s3Object)) {
                                            setupForSignatureDownload(
                                                    s3Object, previousDataFileObjectRef.getAndSet(null));
                                        } else {
                                            previousDataFileObjectRef.set(s3Object);
                                        }
                                    })
                                    .filter(HistoricalDownloader::isSignatureFileObject)
                                    .flatMap(
                                            s3Object -> streamFileProvider.get(s3Object, downloadPath),
                                            downloadConcurrency)
                                    .doOnNext(this::objectDownloadCompleted)
                                    .count()
                                    .block();

                            log.info("Downloaded {} signatures for node: {} in {}", count, node, stopwatch);
                            return count;
                        } catch (Exception e) {
                            log.error("Error downloading signature files for node: {} after {}", node, stopwatch, e);
                            throw e; // Complete exceptionally for now
                        }
                    },
                    executorService));
        }

        var toComplete = downloaders.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(toComplete).join();
        // And do what with the returned counts? Sum -> total sig files downloaded

        executorService.shutdownNow();
    }

    @Scheduled(initialDelay = 20000, fixedDelay = 10000)
    public void manageDownloads() {
        log.trace("manageDownloads - taking a look!");
        var stopwatch = Stopwatch.createStarted();

        long downloadsCompleted = 0L;
        long downloadsStarted = 0L;

        var now = Instant.now().getEpochSecond();
        var shouldBeDoneTime = now - 60L;

        var entryIterator = downloadsInfoMap.entrySet().iterator();
        while (entryIterator.hasNext()) {
            var entry = entryIterator.next();
            var filename = entry.getKey();
            var fileInfo = entry.getValue();

            /*
             * If a sufficient number of signature files have been successfully download, it is time to
             * kick of the download of the stream data file.
             */
            if (fileInfo.isConsensusReached()) {
                if (isSignatureFileName(filename)) {
                    var signatureFileS3Key =
                            fileInfo.getCompletions().iterator().next().s3Key();
                    var dataFileS3Key = s3Prefix(signatureFileS3Key)
                            + fileInfo.getDataFileBaseNameRef().get();
                    log.info(
                            "Sufficient signature files downloaded for {}, starting data download {}",
                            filename,
                            dataFileS3Key);
                    streamFileProvider.get(dataFileS3Key, downloadPath, this::objectDownloadCompletedConsumer);
                    downloadsStarted++;
                } else {
                    log.debug("Data file downloaded for {}", filename);
                }

                fileInfo.getCompletions().clear();
                entryIterator.remove();
                downloadsCompleted++;
                continue;
            }

            /*
             * Consensus has not yet been reached in terms of the number of files (nodes) downloaded. If we
             * "should" be done by now, then perhaps one or more of the 1/3 stake of nodes does not have the
             * file and did not request to download it, or encountered an error.
             */
            if (fileInfo.startTime < shouldBeDoneTime) {
                // It's not just count, but stake...  TBD
                var completions = fileInfo.getCompletions();
                var completionsCount = completions.size();
                var requiredCompletionsCount = fileInfo.consensusNodeInfo.nextIndex;

                log.debug(
                        "File {} has only {} of {} completions", filename, completionsCount, requiredCompletionsCount);
                // TODO take action - download additional consensus file(s), or for data file, try another
                // node prefix.
            }
        }

        log.info("This cycle - downloads completed: {}, downloads started: {}", downloadsCompleted, downloadsStarted);
    }

    private void setupForSignatureDownload(S3Object signatureFileObject, S3Object dataFileObject) {
        var signatureFileKey = signatureFileObject.key();
        var fileDownloadDir = downloadPath.resolve(signatureFileKey).getParent().toFile();
        try {
            FileUtils.forceMkdir(fileDownloadDir);
        } catch (IOException e) {
            throw new FileOperationException("Unable to create local directory %s".formatted(fileDownloadDir), e);
        }

        var fileInfo = this.downloadsInfoMap.computeIfAbsent(
                s3Basename(signatureFileKey), key -> new FileSpecificInfo(this.nodeInfoRef.get(), dataFileObject));

        if (dataFileObject != null) {
            fileInfo.getDataFileBaseNameRef().compareAndSet(null, s3Basename(dataFileObject.key()));
        }
    }

    private GetObjectResponseWithKey objectDownloadCompleted(GetObjectResponseWithKey responseWithKey) {
        var s3Key = responseWithKey.s3Key();

        var downloadInfo = this.downloadsInfoMap.get(s3Basename(s3Key));
        if (downloadInfo != null) { // Consensus not yet reached
            downloadInfo.getCompletions().add(responseWithKey);
        }

        log.debug("Download completed for {}", s3Key);
        return responseWithKey;
    }

    private void objectDownloadCompletedConsumer(GetObjectResponseWithKey responseWithKey) {
        objectDownloadCompleted(responseWithKey);
    }

    /**
     * Returns a randomly-selected (and randomly-ordered) collection of ConsensusNode elements from the input, where the
     * total stake is just enough to meet/exceed the CommonDownloader "downloadRatio" property.
     *
     * @param allNodes the entire set of ConsensusNodes
     * @return a randomly-ordered subcollection
     */
    private ConsensusNodeInfo partialCollection(Collection<ConsensusNode> allNodes) {
        var downloadRatio = downloaderProperties.getCommon().getDownloadRatio();
        // no need to randomize (just return entire list) if # of nodes is 0 or 1 or downloadRatio == 1
        var nodes = new ArrayList<>(allNodes);
        if (allNodes.size() <= 1 || downloadRatio.compareTo(BigDecimal.ONE) == 0) {
            return new ConsensusNodeInfo(Collections.unmodifiableList(nodes), allNodes.size());
        }

        // shuffle nodes into a random order
        Collections.shuffle(nodes);

        long totalStake = nodes.get(0).getTotalStake();
        // only keep "just enough" nodes to reach/exceed downloadRatio
        long neededStake = BigDecimal.valueOf(totalStake)
                .multiply(downloadRatio)
                .setScale(0, RoundingMode.CEILING)
                .longValue();
        long aggregateStake = 0; // sum of the stake of all nodes evaluated so far
        int lastEntry = 0;
        while (aggregateStake < neededStake) {
            aggregateStake += nodes.get(lastEntry++).getStake();
        }

        log.debug(
                "partialCollection: Kept {} of {} nodes, for stake of {} / {}",
                lastEntry,
                allNodes.size(),
                aggregateStake,
                totalStake);

        return new ConsensusNodeInfo(Collections.unmodifiableList(nodes), lastEntry);
    }

    private record ConsensusNodeInfo(List<ConsensusNode> nodes, int nextIndex) {}

    @Value
    private static class FileSpecificInfo {
        Set<GetObjectResponseWithKey> completions;
        ConsensusNodeInfo consensusNodeInfo;
        AtomicReference<String> dataFileBaseNameRef;
        AtomicInteger nextNodeIndex;
        long startTime;

        FileSpecificInfo(ConsensusNodeInfo consensusNodeInfo, S3Object dataFileObject) {
            this.consensusNodeInfo = consensusNodeInfo;
            this.dataFileBaseNameRef =
                    new AtomicReference<>(dataFileObject == null ? null : s3Basename(dataFileObject.key()));
            this.startTime = Instant.now().getEpochSecond();
            this.nextNodeIndex = new AtomicInteger(consensusNodeInfo.nextIndex());
            this.completions = ConcurrentHashMap.newKeySet(consensusNodeInfo.nextIndex());
        }

        boolean isConsensusReached() {
            return completions.size() >= consensusNodeInfo.nextIndex;
        }
    }
}
