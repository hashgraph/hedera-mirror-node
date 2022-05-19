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

import java.math.BigInteger;
import java.util.Collections;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.hedera.mirror.common.converter.WeiBarTinyBarConverter;

@Named
@RequiredArgsConstructor(onConstructor_ = { @Lazy })
public class ConvertEthereumTransactionValueMigration extends MirrorBaseJavaMigration {

    public static final int BATCH_SIZE = 1000;

    private static final String DROP_COLUMN_SQL = "alter table if exists ethereum_transaction " +
            "drop column value_in_weibar";

    private static final String RENAME_ADD_COLUMN_SQL = "alter table if exists ethereum_transaction " +
            "rename column value to value_in_weibar; " +
            "alter table if exists ethereum_transaction add column value bigint null";

    private static final String SELECT_NON_NULL_VALUE_SQL = "select consensus_timestamp, value_in_weibar " +
            "from ethereum_transaction " +
            "where consensus_timestamp > :consensusTimestamp and value_in_weibar is not null " +
            "order by consensus_timestamp " +
            "limit :limit";

    private static final String SET_CONVERTED_VALUE_SQL = "update ethereum_transaction " +
            "set value = :value " +
            "where consensus_timestamp = :consensusTimestamp";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    protected void doMigrate() {
        namedParameterJdbcTemplate.update(RENAME_ADD_COLUMN_SQL, Collections.emptyMap());

        var converter = WeiBarTinyBarConverter.INSTANCE;
        var lastConsensusTimestamp = 0L;
        var paramSource = new MapSqlParameterSource().addValue("limit", BATCH_SIZE);

        while (true) {
            paramSource.addValue("consensusTimestamp", lastConsensusTimestamp);
            var transactions = namedParameterJdbcTemplate.query(SELECT_NON_NULL_VALUE_SQL, paramSource,
                    (rs, index) -> new EthereumTransaction(rs.getLong(1), rs.getBytes(2)));
            if (transactions.size() > 0) {
                var paramSources = new MapSqlParameterSource[transactions.size()];
                int index = 0;
                for (var transaction : transactions) {
                    var value = new BigInteger(transaction.getValueInWeibar());
                    paramSources[index] = new MapSqlParameterSource()
                            .addValue("consensusTimestamp", transaction.getConsensusTimestamp())
                            .addValue("value", converter.weiBarToTinyBar(value));
                    index++;
                }

                lastConsensusTimestamp = transactions.get(index - 1).getConsensusTimestamp();
                namedParameterJdbcTemplate.batchUpdate(SET_CONVERTED_VALUE_SQL, paramSources);
            }

            if (transactions.size() < BATCH_SIZE) {
                break;
            }
        }

        namedParameterJdbcTemplate.update(DROP_COLUMN_SQL, Collections.emptyMap());
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.60.0");
    }

    @Override
    public String getDescription() {
        return "Convert ethereum transaction from weibar to tinybar and change column data type to bigint";
    }

    @Value
    private static class EthereumTransaction {
        private Long consensusTimestamp;
        private byte[] valueInWeibar;
    }
}
