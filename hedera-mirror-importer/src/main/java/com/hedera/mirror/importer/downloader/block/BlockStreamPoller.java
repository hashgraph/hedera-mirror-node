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

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.StreamPoller;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.block.BlockFileReader;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@Named
@RequiredArgsConstructor
public class BlockStreamPoller implements StreamPoller {

    private static final Optional<Long> PRE_GENESIS = Optional.of(-1L);

    private final BlockFileReader blockFileReader;
    private final BlockStreamVerifier blockStreamVerifier;
    private final ConsensusNodeService consensusNodeService;
    private final AtomicReference<Optional<Long>> lastBlockNumber = new AtomicReference<>(Optional.empty());
    private final BlockPollerProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final StreamFileProvider streamFileProvider;

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@blockPollerProperties.getFrequency().toMillis()}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }

        long blockNumber = getNextBlockNumber();
        var bytes = new AtomicReference<byte[]>();
        var filename = BlockFile.getBlockStreamFilename(blockNumber);
        var filePath = new AtomicReference<String>();

        var blockFile = Objects.requireNonNull(Flux.fromIterable(getRandomizedNodes()))
                .flatMap(
                        node -> {
                            long nodeId = node.getNodeId();
                            return streamFileProvider
                                    .get(node, StreamFilename.from(filename))
                                    .doOnError(e -> log.warn(
                                            "Error downloading block file {} from node {}", filename, nodeId, e))
                                    .onErrorResume(e -> Mono.empty())
                                    .doOnNext(s -> {
                                        filePath.set(s.getStreamFilename().getFilePath());
                                        log.debug("Downloaded block file {} from node {}", filename, nodeId);
                                    })
                                    .map(blockFileReader::read)
                                    .doOnError(e ->
                                            log.warn("Error reading block file {} from node {}", filename, nodeId, e))
                                    .onErrorResume(e -> Mono.empty())
                                    .doOnNext(f -> {
                                        bytes.set(f.getBytes());
                                        if (!properties.isPersistBytes()) {
                                            f.setBytes(null);
                                        }
                                    })
                                    .doOnNext(blockStreamVerifier::verify)
                                    .doOnError(e ->
                                            log.warn("Error verifying block file {} from node {}", filename, nodeId, e))
                                    .onErrorResume(e -> Mono.empty());
                        },
                        1)
                .timeout(properties.getCommon().getTimeout())
                .blockFirst();
        if (blockFile == null) {
            log.warn("Failed to download block file {}", filename);
            return;
        }

        if (properties.isWriteFiles()) {
            var importerProperties = properties.getCommon().getImporterProperties();
            Utility.archiveFile(filePath.get(), bytes.get(), importerProperties.getStreamPath());
        }

        lastBlockNumber.set(Optional.of(blockNumber));
    }

    private long getNextBlockNumber() {
        return lastBlockNumber
                .get()
                .or(() -> {
                    var last = recordFileRepository
                            .findLatest()
                            .map(RecordFile::getIndex)
                            .or(() -> PRE_GENESIS);
                    lastBlockNumber.compareAndSet(Optional.empty(), last);
                    return last;
                })
                .map(v -> v + 1)
                .orElse(0L);
    }

    private Collection<ConsensusNode> getRandomizedNodes() {
        var nodes = new ArrayList<>(consensusNodeService.getNodes());
        Collections.shuffle(nodes);
        return nodes;
    }
}
