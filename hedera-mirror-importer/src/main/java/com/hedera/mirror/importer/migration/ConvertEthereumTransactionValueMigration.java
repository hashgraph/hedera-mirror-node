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
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.hedera.mirror.common.converter.WeiBarTinyBarConverter;

@Log4j2
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class ConvertEthereumTransactionValueMigration extends MirrorBaseJavaMigration {

    public static final int BATCH_SIZE = 1000;

    private static final String SELECT_NON_NULL_VALUE_SQL = "select consensus_timestamp, value " +
            "from ethereum_transaction " +
            "where consensus_timestamp > :consensusTimestamp and value is not null " +
            "order by consensus_timestamp " +
            "limit :limit";

    private static final String SET_CONVERTED_VALUE_SQL = "update ethereum_transaction " +
            "set value = :value " +
            "where consensus_timestamp = :consensusTimestamp";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public String getDescription() {
        return "Convert ethereum transaction value from weibar to tinybar";
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.60.0");
    }

    @Override
    protected void doMigrate() {
        var converter = WeiBarTinyBarConverter.INSTANCE;
        long count = 0L;
        var lastConsensusTimestamp = -1L;
        var paramSource = new MapSqlParameterSource().addValue("limit", BATCH_SIZE);
        var stopwatch = Stopwatch.createStarted();

        while (true) {
            paramSource.addValue("consensusTimestamp", lastConsensusTimestamp);
            var transactions = namedParameterJdbcTemplate.query(SELECT_NON_NULL_VALUE_SQL, paramSource,
                    (rs, index) -> new EthereumTransaction(rs.getLong(1), rs.getBytes(2)));
            if (transactions.size() > 0) {
                var paramSources = new MapSqlParameterSource[transactions.size()];
                int index = 0;
                for (var transaction : transactions) {
                    paramSources[index] = new MapSqlParameterSource()
                            .addValue("consensusTimestamp", transaction.getConsensusTimestamp())
                            .addValue("value", converter.weiBarToTinyBar(transaction.getValue()));
                    index++;
                }

                lastConsensusTimestamp = transactions.get(index - 1).getConsensusTimestamp();
                namedParameterJdbcTemplate.batchUpdate(SET_CONVERTED_VALUE_SQL, paramSources);
            }

            count += transactions.size();

            if (transactions.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info("Successfully converted value from weibar to tinybar for {} transactions in {}", count, stopwatch);
    }

    @Value
    private static class EthereumTransaction {
        private Long consensusTimestamp;
        private byte[] value;
    }
}
