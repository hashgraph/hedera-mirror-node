/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.MAINNET;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.TESTNET;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;

@Named
public class BlockNumberMigration extends RepeatableMigration {

    static final Map<HederaNetwork, Pair<Long, Long>> BLOCK_NUMBER_MAPPING = Map.of(
            TESTNET, Pair.of(1656461617493248000L, 22384256L),
            MAINNET, Pair.of(1656461547557609267L, 34305852L));

    private final MirrorProperties mirrorProperties;
    private final RecordFileRepository recordFileRepository;

    @Lazy
    public BlockNumberMigration(MirrorProperties mirrorProperties, RecordFileRepository recordFileRepository) {
        super(mirrorProperties.getMigration());
        this.mirrorProperties = mirrorProperties;
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.67.0");
    }

    @Override
    protected void doMigrate() {
        var network = mirrorProperties.getNetwork();
        var consensusEndAndBlockNumber = BLOCK_NUMBER_MAPPING.get(network);

        if (consensusEndAndBlockNumber == null) {
            log.info("No block migration necessary for {} network", network);
            return;
        }

        long correctConsensusEnd = consensusEndAndBlockNumber.getKey();
        long correctBlockNumber = consensusEndAndBlockNumber.getValue();

        recordFileRepository
                .findById(correctConsensusEnd)
                .map(RecordFile::getIndex)
                .filter(blockNumber -> blockNumber != correctBlockNumber)
                .ifPresent(blockNumber -> updateIndex(correctBlockNumber, blockNumber));
    }

    private void updateIndex(long correctBlockNumber, long incorrectBlockNumber) {
        long offset = correctBlockNumber - incorrectBlockNumber;
        Stopwatch stopwatch = Stopwatch.createStarted();
        int count = recordFileRepository.updateIndex(offset);
        log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
    }
}
