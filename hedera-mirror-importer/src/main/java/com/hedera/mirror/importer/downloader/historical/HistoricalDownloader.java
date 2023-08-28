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
import com.hedera.mirror.importer.leader.Leader;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.model.S3Object;

@Named
@Slf4j
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
    private final int downloadConcurrency = 3;

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
        //        var nodeInfo = partialCollection(consensusNodeService.getNodes());
        var nodeInfo = partialCollection(
                List.of(consensusNodeService.getNodes().iterator().next())); // TODO Single node for initial debug
        nodeInfoRef.set(nodeInfo);

        Set<CompletableFuture<Long>> downloaders = new HashSet<>(nodeInfo.nextIndex);
        for (int nodeIdx = 0; nodeIdx < nodeInfo.nextIndex; nodeIdx++) {
            var node = nodeInfo.nodes().get(nodeIdx);

            downloaders.add(CompletableFuture.supplyAsync(
                    () -> {
                        var stopwatch = Stopwatch.createStarted();

                        try {
                            var count = streamFileProvider
                                    .listAllPaginated(node, startFilename)
                                    .filter(s3Object -> s3Object.key().endsWith("_sig"))
                                    .doOnNext(this::selectForDownload)
                                    .flatMap(
                                            s3Object -> streamFileProvider.get(s3Object, downloadPath),
                                            downloadConcurrency)
                                    .doOnNext(responseWithKey -> downloadCompleted(responseWithKey, node))
                                    .count()
                                    .block();

                            log.info("Downloaded {} signatures for node: {} in {}", count, node, stopwatch);
                            return count;
                        } catch (Exception e) {
                            log.error("Error downloading signature files for node: {} after {}", node, stopwatch, e);
                            throw e; // Complete exceptionally
                        }
                    },
                    executorService));
        }

        var toComplete = downloaders.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(toComplete).join();

        executorService.shutdownNow();
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void manageDownloads() {
        log.info("manageDownloads - taking a look!");
        var stopwatch = Stopwatch.createStarted();

        long downloadsRemoved = 0L;

        var now = Instant.now().getEpochSecond();
        var shouldBeDoneTime = now - 60L;

        var entryIterator = downloadsInfoMap.entrySet().iterator();
        while (entryIterator.hasNext()) {
            var entry = entryIterator.next();
            var filename = entry.getKey();
            var fileInfo = entry.getValue();

            // It's not just count, but stake...  TBD
            var completionsCount = fileInfo.getCompletions().size();
            var requiredCompletionsCount = fileInfo.consensusNodeInfo.nodes().size();

            if (completionsCount >= requiredCompletionsCount) {
                log.info("Sufficient signature files downloaded for {}", filename);
                fileInfo.getCompletions().clear();
                entryIterator.remove();
                downloadsRemoved++;
                continue;
            }

            /*
             * Consensus has not yet been reached in terms of the number of files (nodes) downloaded. If we
             * "should" be done by now, then perhaps one or more of the 1/3 stake of nodes does not have the
             * file and did not request to download it.
             */
            if (fileInfo.startTime < shouldBeDoneTime) {
                log.info("File {} has only {} of {} completions", filename, completionsCount, requiredCompletionsCount);
            }
        }
    }

    private void selectForDownload(S3Object s3Object) {
        var s3Basename = s3Basename(s3Object.key());
        this.downloadsInfoMap.computeIfAbsent(s3Basename, key -> new FileSpecificInfo(this.nodeInfoRef.get()));
    }

    private GetObjectResponseWithKey downloadCompleted(GetObjectResponseWithKey responseWithKey, ConsensusNode node) {
        var s3Basename = s3Basename(responseWithKey.s3Key());
        var downloadInfo = this.downloadsInfoMap.get(s3Basename);

        if (downloadInfo != null) { // Consensus not yet met
            downloadInfo.getCompletions().add(responseWithKey);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Download completed for node: {}, filename: {}, size: {}",
                    node,
                    s3Basename,
                    responseWithKey.getObjectResponse().contentLength());
        }

        return responseWithKey;
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
        long startTime;
        ConsensusNodeInfo consensusNodeInfo;
        AtomicInteger nextNodeIndex;
        Set<GetObjectResponseWithKey> completions;

        FileSpecificInfo(ConsensusNodeInfo consensusNodeInfo) {
            this.consensusNodeInfo = consensusNodeInfo;
            this.startTime = Instant.now().getEpochSecond();
            this.nextNodeIndex = new AtomicInteger(consensusNodeInfo.nextIndex());
            this.completions = ConcurrentHashMap.newKeySet(consensusNodeInfo.nextIndex());
        }
    }
}
