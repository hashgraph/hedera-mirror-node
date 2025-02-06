/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.StreamPoller;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.block.BlockFileReader;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@RequiredArgsConstructor
final class BlockStreamPoller implements StreamPoller {

    private static final long GENESIS_BLOCK_NUMBER = 0;

    private final BlockFileReader blockFileReader;
    private final BlockStreamVerifier blockStreamVerifier;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final ConsensusNodeService consensusNodeService;
    private final BlockPollerProperties properties;
    private final StreamFileProvider streamFileProvider;

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@blockPollerProperties.getFrequency().toMillis()}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }

        long blockNumber = getNextBlockNumber();
        var nodes = getRandomizedNodes();
        var stopwatch = Stopwatch.createStarted();
        var streamFilename = StreamFilename.from(blockNumber);
        String filename = streamFilename.getFilename();
        var streamPath = commonDownloaderProperties.getImporterProperties().getStreamPath();
        var timeout = commonDownloaderProperties.getTimeout();

        for (int i = 0; i < nodes.size() && timeout.isPositive(); i++) {
            var node = nodes.get(i);
            long nodeId = node.getNodeId();

            try {
                var blockFileData = streamFileProvider
                        .get(node, streamFilename)
                        .blockOptional(timeout)
                        .orElseThrow();
                log.debug("Downloaded block file {} from node {}", filename, nodeId);

                var blockFile = blockFileReader.read(blockFileData);
                blockFile.setNodeId(nodeId);

                byte[] bytes = blockFile.getBytes();
                if (!properties.isPersistBytes()) {
                    blockFile.setBytes(null);
                }

                blockStreamVerifier.verify(blockFile);

                if (properties.isWriteFiles()) {
                    Utility.archiveFile(blockFileData.getFilePath(), bytes, streamPath);
                }

                return;
            } catch (Throwable t) {
                log.error("Failed to process block file {} from node {}", filename, nodeId, t);
            }

            timeout = commonDownloaderProperties.getTimeout().minus(stopwatch.elapsed());
        }

        log.warn("Failed to download block file {}", filename);
    }

    private long getNextBlockNumber() {
        return blockStreamVerifier.getLastBlockNumber().map(v -> v + 1).orElseGet(() -> {
            var startBlockNumber =
                    commonDownloaderProperties.getImporterProperties().getStartBlockNumber();
            if (startBlockNumber != null) {
                return startBlockNumber;
            }

            return GENESIS_BLOCK_NUMBER;
        });
    }

    private List<ConsensusNode> getRandomizedNodes() {
        var nodes = new ArrayList<>(consensusNodeService.getNodes());
        Collections.shuffle(nodes);
        return nodes;
    }
}
