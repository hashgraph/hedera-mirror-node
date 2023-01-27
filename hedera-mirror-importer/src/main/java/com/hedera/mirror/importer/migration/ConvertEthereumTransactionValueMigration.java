package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.hedera.mirror.common.converter.WeiBarTinyBarConverter;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class ConvertEthereumTransactionValueMigration extends MirrorBaseJavaMigration {

    private static final String SELECT_NON_NULL_VALUE_SQL = "select consensus_timestamp, value " +
            "from ethereum_transaction " +
            "where value is not null and length(value) > 0 " +
            "order by consensus_timestamp";

    private static final String SET_TINYBAR_VALUE_SQL = "update ethereum_transaction " +
            "set value = :value " +
            "where consensus_timestamp = :consensusTimestamp";

    private final NamedParameterJdbcTemplate jdbcTemplate;

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
        var count = new AtomicLong(0);
        var stopwatch = Stopwatch.createStarted();

        jdbcTemplate.query(SELECT_NON_NULL_VALUE_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var weibar = rs.getBytes(2);
            var tinybar = converter.convert(weibar, true);
            jdbcTemplate.update(SET_TINYBAR_VALUE_SQL,
                    Map.of("consensusTimestamp", consensusTimestamp, "value", tinybar));
            count.incrementAndGet();
        });

        log.info("Successfully converted value from weibar to tinybar for {} ethereum transactions in {}",
                count.get(), stopwatch);
    }
}
