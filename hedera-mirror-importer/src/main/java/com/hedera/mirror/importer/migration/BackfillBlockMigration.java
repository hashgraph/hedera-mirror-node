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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.common.aggregator.LogsBloomAggregator;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Named
public class BackfillBlockMigration extends AsyncJavaMigration<Long> {

    private static final String SELECT_CONTRACT_RESULT = "select bloom, gas_used " +
            "from contract_result cr " +
            "join transaction t on t.consensus_timestamp = cr.consensus_timestamp " +
            "where cr.consensus_timestamp >= :consensusStart " +
            "  and cr.consensus_timestamp <= :consensusEnd " +
            "  and t.nonce = 0";

    private static final String SET_TRANSACTION_INDEX = "with indexed as ( " +
            "  select consensus_timestamp, row_number() over (order by consensus_timestamp) - 1 as index " +
            "  from transaction " +
            "  where consensus_timestamp >= :consensusStart" +
            "    and consensus_timestamp <= :consensusEnd " +
            "  order by consensus_timestamp) " +
            "update transaction t " +
            "set index = indexed.index " +
            "from indexed " +
            "where t.consensus_timestamp = indexed.consensus_timestamp";

    private final RecordFileRepository recordFileRepository;

    @Lazy
    public BackfillBlockMigration(DBProperties dbProperties, NamedParameterJdbcTemplate jdbcTemplate,
                                  RecordFileRepository recordFileRepository,
                                  TransactionOperations transactionOperations) {
        super(jdbcTemplate, dbProperties.getSchema(), transactionOperations);
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    public String getDescription() {
        return "Backfill block gasUsed, logsBloom, and transaction index";
    }

    @Override
    protected Long getInitial() {
        return Long.MAX_VALUE;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.61.1");
    }

    @Override
    protected int getSuccessChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    /**
     * Backfills information for the record file immediately before the consensus end timestamp of the last record file.
     *
     * @param lastConsensusEnd The consensus end timestamp of the last record file
     * @return The consensus end of the processed record file or null if no record file is processed
     */
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusEnd) {
        return recordFileRepository.findLatestMissingGasUsedBefore(lastConsensusEnd).map(recordFile -> {
            var queryParams = Map.of("consensusStart", recordFile.getConsensusStart(),
                    "consensusEnd", recordFile.getConsensusEnd());

            var bloomAggregator = new LogsBloomAggregator();
            AtomicLong gasUsed = new AtomicLong(0);
            jdbcTemplate.query(SELECT_CONTRACT_RESULT, queryParams, rs -> {
                bloomAggregator.aggregate(rs.getBytes("bloom"));
                gasUsed.addAndGet(rs.getLong("gas_used"));
            });

            recordFile.setGasUsed(gasUsed.get());
            recordFile.setLogsBloom(bloomAggregator.getBloom());
            recordFileRepository.save(recordFile);

            // set transaction index for the transactions in the record file
            jdbcTemplate.update(SET_TRANSACTION_INDEX, queryParams);

            return recordFile.getConsensusEnd();
        });
    }
}
