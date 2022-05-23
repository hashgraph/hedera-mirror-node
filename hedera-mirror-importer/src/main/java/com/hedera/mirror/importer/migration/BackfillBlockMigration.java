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

import com.google.common.base.Stopwatch;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.aggregator.LogsBloomAggregator;

@Named
@Log4j2
public class BackfillBlockMigration extends AsyncJavaMigration {

    private static final String SELECT_BLOOM_FILTERS_IN_RECORD_FILE = "select bloom " +
            "from contract_result " +
            "where consensus_timestamp >= :consensusStart " +
            "  and consensus_timestamp <= :consensusEnd " +
            "  and bloom is not null " +
            "  and length(bloom) > 0 " +
            "order by consensus_timestamp";

    private static final String SELECT_PREVIOUS_RECORD_FILE_TIMESTAMP_RANGE = "select consensus_start, consensus_end " +
            "from record_file " +
            "where consensus_end < :lastConsensusEnd and gas_used = -1 " +
            "order by consensus_end desc limit 1";

    private static final String SET_RECORD_FILE_BLOOM_FILTER = "update record_file " +
            "set logs_bloom = :bloomFilter " +
            "where consensus_end = :consensusEnd";

    private static final String SET_RECORD_FILE_GAS_USED_SQL = "update record_file " +
            "set gas_used = coalesce(gas.total, 0) " +
            "from (" +
            "  select sum(gas_used) as total " +
            "  from contract_result cr " +
            "  join transaction t on t.consensus_timestamp = cr.consensus_timestamp " +
            "  where cr.consensus_timestamp >= :consensusStart " +
            "    and cr.consensus_timestamp <= :consensusEnd " +
            "    and t.nonce = 0 " +
            "    and gas_used is not null " +
            ") gas " +
            "where consensus_end = :consensusEnd";

    private static final String SET_TRANSACTION_INDEX_SQL = "with indexed as ( " +
            "  select consensus_timestamp, row_number() over (order by consensus_timestamp) - 1 as index " +
            "  from transaction " +
            "  where consensus_timestamp >= :consensusStart" +
            "    and consensus_timestamp <= :consensusEnd " +
            "    and index is null " +
            "  order by consensus_timestamp) " +
            "update transaction t " +
            "set index = indexed.index " +
            "from indexed " +
            "where t.consensus_timestamp = indexed.consensus_timestamp";

    private final TransactionTemplate transactionTemplate;

    @Lazy
    public BackfillBlockMigration(NamedParameterJdbcTemplate jdbcTemplate,
                                  @Value("${hedera.mirror.importer.db.schema}") CharSequence schema,
                                  TransactionTemplate transactionTemplate) {
        super(jdbcTemplate, schema);
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected void migrateAsync() {
        long count = 0;
        Long lastConsensusEnd = Long.MAX_VALUE;
        var stopwatch = Stopwatch.createStarted();
        boolean success = false;

        try {
            while (true) {
                lastConsensusEnd = backfillRecordFile(lastConsensusEnd);
                if (lastConsensusEnd == null) {
                    break;
                }

                count++;
                log.debug("Finished processing record file with consensus end {}", lastConsensusEnd);
            }

            success = true;
        } finally {
            var simpleClassName = getClass().getSimpleName();
            if (success) {
                log.info("{} successfully processed {} record files in {}", simpleClassName, count, stopwatch);
            } else {
                log.info("{} processed {} record files but failed in {}", simpleClassName, count, stopwatch);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Backfill block gasUsed, logsBloom, and transaction index";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.60.1");
    }

    @Override
    protected int getSuccessChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    private byte[] aggregateBloomFilters(List<byte[]> bloomFilters) {
        var aggregator = new LogsBloomAggregator();
        bloomFilters.forEach(aggregator::aggregate);
        return aggregator.getBloom();
    }

    /**
     * Backfills information for the record file immediately before the consensus end timestamp of the last record file
     *
     * @param lastConsensusEnd The consensus end timestamp of the last record file
     * @return The consensus end of the processed record file or null if no record file is processed
     */
    private Long backfillRecordFile(long lastConsensusEnd) {
        try {
            return transactionTemplate.execute((status) -> {
                var timestampRange = jdbcTemplate.query(
                        SELECT_PREVIOUS_RECORD_FILE_TIMESTAMP_RANGE,
                        Map.of("lastConsensusEnd", lastConsensusEnd),
                        (rs) -> rs.next() ? new RecordFileTimestampRange(rs.getLong("consensus_start"),
                                rs.getLong("consensus_end")) : null);
                if (timestampRange == null) {
                    return null;
                }
                
                var timestampRangeParams = Map.of("consensusStart", timestampRange.getConsensusStart(),
                        "consensusEnd", timestampRange.getConsensusEnd());

                var bloomFilters = jdbcTemplate.queryForList(SELECT_BLOOM_FILTERS_IN_RECORD_FILE,
                        timestampRangeParams, byte[].class);
                var aggregatedBloomFilter = aggregateBloomFilters(bloomFilters);
                var bloomFilterParams = Map.of("consensusEnd", timestampRange.getConsensusEnd(),
                        "bloomFilter", aggregatedBloomFilter);
                jdbcTemplate.update(SET_RECORD_FILE_BLOOM_FILTER, bloomFilterParams);

                // gas used
                jdbcTemplate.update(SET_RECORD_FILE_GAS_USED_SQL, timestampRangeParams);

                // transaction index
                jdbcTemplate.update(SET_TRANSACTION_INDEX_SQL, timestampRangeParams);

                return timestampRange.getConsensusEnd();
            });
        } catch (Exception ex) {
            log.error("Failed to backfill information for record file before {}: ", lastConsensusEnd, ex);
            throw ex;
        }
    }

    @lombok.Value
    private static class RecordFileTimestampRange {
        private Long consensusStart;
        private Long consensusEnd;
    }
}
