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

import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_STREAM_DOWNLOADER;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.leader.Leader;
import jakarta.inject.Named;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.CustomLog;
import org.apache.commons.io.FileUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.model.S3Object;

@Named
@CustomLog
public class HistoricalDownloader {

    public static final String SEPARATOR = "/";
    private static final String TEMPLATE_ACCOUNT_ID_PREFIX = "%s/%s%s/";

    private final ConsensusNodeService consensusNodeService;
    private final Path downloadPath;
    private final DownloaderProperties downloaderProperties;
    private final ExecutorService executorService = Executors.newFixedThreadPool(100);
    private final StreamFileProvider streamFileProvider;
    private final StreamType streamType;
    private final Cache dataDownloadsCache;
    private final int downloadConcurrency = 3; // Debug, make a property

    public HistoricalDownloader(
            ConsensusNodeService consensusNodeService,
            HistoricalDownloaderProperties downloaderProperties,
            StreamFileProvider streamFileProvider,
            @Named(CACHE_MANAGER_STREAM_DOWNLOADER) CacheManager cacheManager) {

        this.consensusNodeService = consensusNodeService;
        this.dataDownloadsCache = cacheManager.getCache("dataDownloads");
        this.downloaderProperties = downloaderProperties;
        this.streamFileProvider = streamFileProvider;
        this.streamType = downloaderProperties.getStreamType();
        this.downloadPath = downloaderProperties.getMirrorProperties().getDownloadPath();
    }

    private static String getPrefix(ConsensusNode node, StreamType streamType) {
        var nodeAccount = node.getNodeAccountId().toString();
        return TEMPLATE_ACCOUNT_ID_PREFIX.formatted(streamType.getPath(), streamType.getNodePrefix(), nodeAccount);
    }

    private static String s3Basename(String s3Key) {
        var lastSeparatorIndex = s3Key.lastIndexOf(SEPARATOR);
        return lastSeparatorIndex < 0 ? s3Key : s3Key.substring(lastSeparatorIndex + 1); // keep trailing /
    }

    private static boolean isSignatureFileName(String filename) {
        return filename.endsWith(StreamType.SIGNATURE_SUFFIX);
    }

    @Leader
    // Run once
    @Scheduled(initialDelay = 1000 * 5, fixedDelay = Long.MAX_VALUE)
    public void downloadAll() {
        downloadAll(StreamFilename.EPOCH);
    }

    public void downloadAll(StreamFilename startFilename) {

        var methodStopwatch = Stopwatch.createStarted();
        log.info("Starting download from {}} for stream type {}", startFilename, streamType);

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
        var nodes = partialCollection(consensusNodeService.getNodes());
        //        var nodes = partialCollection(List.of(consensusNodeService.getNodes().iterator().next()));

        List<CompletableFuture<Long>> nodeDownloaders = new ArrayList<>(nodes.size());

        for (var node : nodes) {
            nodeDownloaders.add(CompletableFuture.supplyAsync(
                    () -> {
                        log.info("Downloading files for node {}", node);
                        var stopwatch = Stopwatch.createStarted();

                        var prefix = getPrefix(node, streamType);
                        var filesDownloadDir = downloadPath.resolve(prefix).toFile();
                        try {
                            FileUtils.forceMkdir(filesDownloadDir);
                        } catch (IOException e) {
                            throw new FileOperationException(
                                    "Unable to create local directory %s".formatted(filesDownloadDir), e);
                        }

                        try {
                            var count = streamFileProvider
                                    .listAllPaginated(node, startFilename)
                                    .log()
                                    .filter(this::isDownloadProspect)
                                    .flatMap(streamFileProvider::getAsFile, downloadConcurrency)
                                    .count()
                                    .block();

                            log.info("Downloaded {} files for node: {} in {}", count, node, stopwatch);
                            return count;
                        } catch (Exception e) {
                            log.error("Error downloading files for node: {} after {}", node, stopwatch, e);
                            throw e; // Complete exceptionally for now
                        }
                    },
                    executorService));
        }

        var toComplete = nodeDownloaders.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(toComplete).join();

        Map<Boolean, List<CompletableFuture<Long>>> completionsMap =
                nodeDownloaders.stream().collect(groupingBy(CompletableFuture::isCompletedExceptionally));

        var completedExceptionally = completionsMap.get(Boolean.TRUE);
        var completedSuccessfully = completionsMap.get(Boolean.FALSE);

        if (completedSuccessfully == null) {
            log.warn("All node downloaders completed exceptionally. Some files may have been downloaded.");
        } else {
            var totalFilesDownload = completedSuccessfully.stream()
                    .mapToLong(future -> future.getNow(0L))
                    .sum();
            log.info("Total download time {}, total number of files {}", methodStopwatch, totalFilesDownload);
        }

        if (completedExceptionally != null) {
            log.warn(
                    "{} downloader(s) terminated exceptionally, and file count may be greater than reported",
                    completedExceptionally.size());
        }

        executorService.shutdownNow();
    }

    private boolean isDownloadProspect(S3Object s3Object) {
        var s3Basename = s3Basename(s3Object.key());
        return isSignatureFileName(s3Basename) || dataDownloadsCache.putIfAbsent(s3Basename, s3Basename) == null;
    }

    /**
     * Returns a randomly-selected (and randomly-ordered) collection of CondensusNode elements from the input, where the
     * total stake is just enough to meet/exceed the CommonDownloader "downloadRatio" property.
     *
     * @param allNodes the entire set of ConsensusNodes
     * @return a randomly-ordered subcollection
     */
    private Collection<ConsensusNode> partialCollection(Collection<ConsensusNode> allNodes) {
        var downloadRatio = downloaderProperties.getCommon().getDownloadRatio();
        // no need to randomize (just return entire list) if # of nodes is 0 or 1 or downloadRatio == 1
        if (allNodes.size() <= 1 || downloadRatio.compareTo(BigDecimal.ONE) == 0) {
            return allNodes;
        }

        var nodes = new ArrayList<>(allNodes);
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
        return nodes.subList(0, lastEntry);
    }
}
