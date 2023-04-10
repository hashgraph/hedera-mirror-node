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
import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;

@Named
public class SyntheticTokenAllowanceOwnerMigration extends RepeatableMigration {

    private static final String UPDATE_TOKEN_ALLOWANCE_OWNER_SQL = """
            with affected as (
              select ta.*, cr.consensus_timestamp, cr.sender_id
              from (
                select * from token_allowance
                union all
                select * from token_allowance_history
              ) ta
              join contract_result cr on cr.consensus_timestamp = lower(ta.timestamp_range)
            ), delete_token_allowance as (
              delete from token_allowance ta
              using affected a
              where ta.owner = a.owner and ta.spender = a.spender and ta.token_id = a.token_id
            ), delete_token_allowance_history as (
              delete from token_allowance_history ta
              using affected a
              where ta.owner = a.owner and ta.spender = a.spender and ta.token_id = a.token_id and ta.timestamp_range = a.timestamp_range
            )
            select amount, sender_id as owner, payer_account_id, spender, int8range(consensus_timestamp, null) timestamp_range, token_id
            from affected
            order by consensus_timestamp;
            """;

    private static DataClassRowMapper<TokenAllowance> resultRowMapper = new DataClassRowMapper<>(TokenAllowance.class);
    private final JdbcOperations jdbcOperations;
    private final SqlEntityListener sqlEntityListener;
    private final TransactionTemplate transactionTemplate;

    @Lazy
    public SyntheticTokenAllowanceOwnerMigration(JdbcOperations jdbcOperations,
                                                 MirrorProperties mirrorProperties,
                                                 SqlEntityListener sqlEntityListener,
                                                 TransactionTemplate transactionTemplate) {
        super(mirrorProperties.getMigration());
        this.jdbcOperations = jdbcOperations;
        this.sqlEntityListener = sqlEntityListener;
        this.transactionTemplate = transactionTemplate;

        var defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PGobject.class, Range.class,
                source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(Long.class, EntityId.class,
                AccountIdConverter.INSTANCE::convertToEntityAttribute);
        resultRowMapper.setConversionService(defaultConversionService);
    }

    @Override
    public String getDescription() {
        return "Updates the owner for synthetic token allowances to the corresponding contract result sender";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where contract_result sender_id was added
        return MigrationVersion.fromVersion("1.58.4");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        var tokenAllowances = jdbcOperations.query(UPDATE_TOKEN_ALLOWANCE_OWNER_SQL, resultRowMapper);
        if (!tokenAllowances.isEmpty()) {
            sqlEntityListener.onStart();
            for (var tokenAllowance : tokenAllowances) {
                sqlEntityListener.onTokenAllowance(tokenAllowance);
            }
            transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(null));
        }

        log.info("Updated {} crypto approve allowance owners in {}", tokenAllowances.size(), stopwatch);
    }
}
