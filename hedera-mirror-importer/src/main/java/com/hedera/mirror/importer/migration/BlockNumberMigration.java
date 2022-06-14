package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.MAINNET;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.TESTNET;

import com.google.common.base.Stopwatch;
import java.util.Map;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class BlockNumberMigration extends MirrorBaseJavaMigration {

    static final Map<HederaNetwork, Pair<Long, Long>> BLOCK_NUMBER_MAPPING = Map.of(
            TESTNET, Pair.of(1654885757958366469L, 21831887L),
            MAINNET, Pair.of(1654885703747893000L, 33522048L)
    );

    private final JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;
    private final RecordFileRepository recordFileRepository;

    @Override
    public Integer getChecksum() {
        return 3; // Change this if this migration should be rerun
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.61.1");
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
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

        recordFileRepository.findById(correctConsensusEnd)
                .map(RecordFile::getIndex)
                .filter(blockNumber -> blockNumber != correctBlockNumber)
                .ifPresent(blockNumber -> updateRecordFilesBlockNumber(correctBlockNumber, blockNumber));
    }

    private void updateRecordFilesBlockNumber(long correctBlockNumber, long incorrectBlockNumber) {
        long offset = correctBlockNumber - incorrectBlockNumber;
        Stopwatch stopwatch = Stopwatch.createStarted();
        long count = jdbcTemplate.update("update record_file set index = index + ?", offset);
        log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
    }
}
