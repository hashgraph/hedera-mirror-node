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
import javax.inject.Named;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Named
@Log4j2
public class BackfillBlockMigration extends AsyncJavaMigration {

    private static final String SELECT_BLOOM_FILTERS_IN_RECORD_FILE = "select bloom " +
            "from contract_result " +
            "where consensus_timestamp >= :consensusStart " +
            "  and consensus_timestamp <= :consensusEnd " +
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
            "select consensus_timestamp, row_number() over (order by consensus_timestamp) - 1 as index " +
            "from transaction " +
            "where consensus_timestamp >= :consensusStart and consensus_timestamp <= :consensusEnd " +
            "order by consensus_timestamp) " +
            "update transaction t " +
            "set index = indexed.index " +
            "from indexed " +
            "where t.consensus_timestamp = indexed.consensus_timestamp";

    private final TransactionTemplate transactionTemplate;

    @Lazy
    public BackfillBlockMigration(NamedParameterJdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        super(jdbcTemplate);
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
        return MigrationVersion.fromVersion("1.59.1");
    }

    @Override
    protected int getSuccessChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    @Override
    public MigrationVersion getVersion() {
        return null; // Repeatable migration
    }

    private byte[] aggregateBloomFilters(List<byte[]> bloomFilters) {
        if (bloomFilters.isEmpty()) {
            return null;
        }

        var bloomFilter = bloomFilters.get(0);
        for (var current : bloomFilters.subList(1, bloomFilters.size())) {
            for (int i = 0; i < bloomFilter.length; i++) {
                bloomFilter[i] |= current[i];
            }
        }

        return bloomFilter;
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
                        new MapSqlParameterSource().addValue("lastConsensusEnd", lastConsensusEnd),
                        (rs) -> {
                            if (!rs.next()) {
                                return null;
                            }

                            var range = new RecordFileTimestampRange();
                            range.setConsensusEnd(rs.getLong("consensus_end"));
                            range.setConsensusStart(rs.getLong("consensus_start"));
                            return range;
                        });
                if (timestampRange == null) {
                    return null;
                }

                var timestampRangeParameterSource = new MapSqlParameterSource()
                        .addValue("consensusStart", timestampRange.getConsensusStart())
                        .addValue("consensusEnd", timestampRange.getConsensusEnd());

                var bloomFilters = jdbcTemplate.queryForList(SELECT_BLOOM_FILTERS_IN_RECORD_FILE,
                        timestampRangeParameterSource, byte[].class);
                var recordFileBloomFilter = aggregateBloomFilters(bloomFilters);
                if (recordFileBloomFilter != null) {
                    var bloomFilterParameterSource = new MapSqlParameterSource()
                            .addValue("consensusEnd", timestampRange.getConsensusEnd())
                            .addValue("bloomFilter", recordFileBloomFilter);
                    jdbcTemplate.update(SET_RECORD_FILE_BLOOM_FILTER, bloomFilterParameterSource);
                }

                // gas used
                jdbcTemplate.update(SET_RECORD_FILE_GAS_USED_SQL, timestampRangeParameterSource);

                // transaction index
                jdbcTemplate.update(SET_TRANSACTION_INDEX_SQL, timestampRangeParameterSource);

                return timestampRange.getConsensusEnd();
            });
        } catch (Exception ex) {
            log.error("Failed to backfill information for record file before {}: ", lastConsensusEnd, ex);
            throw ex;
        }
    }

    @Data
    private static class RecordFileTimestampRange {
        Long consensusStart;
        Long consensusEnd;
    }
}
