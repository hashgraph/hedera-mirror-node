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

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.MirrorProperties;

import com.hedera.mirror.importer.repository.RecordFileRepository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.inject.Named;

import java.util.Map;

import static com.hedera.mirror.importer.MirrorProperties.*;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.MAINNET;
import static com.hedera.mirror.importer.MirrorProperties.HederaNetwork.TESTNET;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class BlockNumberMigration extends MirrorBaseJavaMigration {

    private static final MigrationVersion MINIMUM_REQUIRED_VERSION = MigrationVersion.fromVersion("1.57.1");

    private final JdbcTemplate jdbcTemplate;

    private final MirrorProperties mirrorProperties;

    private static final Map<HederaNetwork, Pair<Long, Long>> CONSENSUS_END_BLOCK_NUMBER_PER_NET =
            Map.of(
                    TESTNET, Pair.of(1651500123344619566L, 20484961L),
                    MAINNET, Pair.of( 1651500089855822000L, 20484947L));

    private final RecordFileRepository recordFileRepository;

    @Override
    protected void doMigrate() {
        if (shouldNotMigrateOnCurrentNetwork()) {
            return ;
        }
        var consensusEndAndBlockNumber = CONSENSUS_END_BLOCK_NUMBER_PER_NET.get(mirrorProperties.getNetwork());
        long correctConsensusEnd = consensusEndAndBlockNumber.getKey();
        long correctBlockNumber = consensusEndAndBlockNumber.getValue();

        recordFileRepository.findById(correctConsensusEnd)
                .map(RecordFile::getIndex)
                .filter(blockNumber -> blockNumber != correctBlockNumber)
                .ifPresent(blockNumber -> updateRecordFilesBlockNumber(correctBlockNumber, blockNumber));
    }

    private boolean shouldNotMigrateOnCurrentNetwork() {
        HederaNetwork currentNetwork = mirrorProperties.getNetwork();
        return currentNetwork != TESTNET && currentNetwork != MAINNET;
    }

    private void updateRecordFilesBlockNumber(long correctBlockNumber, long incorrectBlockNumber) {
        long offset = correctBlockNumber - incorrectBlockNumber;
        jdbcTemplate.update("update record_file set index = index + ?", offset);
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MINIMUM_REQUIRED_VERSION;
    }

    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary.";
    }
}
