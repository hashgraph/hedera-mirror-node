/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.batch;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

/**
 * Stateless writer to upsert rows into PostgreSQL using COPY into a temp table then insert and update into final table
 */
@CustomLog
public class BatchUpserter extends BatchInserter {

    private final String finalTableName;
    private final String tempTableSql;
    private final String upsertSql;
    private final Timer upsertMetric;

    public BatchUpserter(
            Class<?> entityClass,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties properties,
            UpsertQueryGenerator upsertQueryGenerator) {
        super(entityClass, dataSource, meterRegistry, properties, upsertQueryGenerator.getTemporaryTableName());
        var createTempIndexSql = upsertQueryGenerator.getCreateTempIndexQuery();
        var createTempTableSql = upsertQueryGenerator.getCreateTempTableQuery();
        var truncateSql = String.format("truncate table %s restart identity cascade", tableName);
        tempTableSql = StringUtils.joinWith(";\n", createTempTableSql, createTempIndexSql, truncateSql);
        finalTableName = upsertQueryGenerator.getFinalTableName();
        upsertSql = upsertQueryGenerator.getUpsertQuery();
        log.trace("Table: {}, Entity: {}, upsertSql:\n{}", finalTableName, entityClass, upsertSql);
        upsertMetric = Timer.builder("hedera.mirror.importer.parse.upsert")
                .description("Time to insert transaction information from temp to final table")
                .tag("table", finalTableName)
                .register(meterRegistry);
    }

    @Override
    protected void persistItems(Collection<?> items, Connection connection) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        try {
            // create temp table to copy into
            createTempTable(connection);

            // copy items to temp table
            super.persistItems(items, connection);

            // Upsert items from the temporary table to the final table
            upsert(connection);
        } catch (Exception e) {
            throw new ParserException(
                    String.format("Error copying %d items to table %s", items.size(), finalTableName), e);
        }
    }

    private void createTempTable(Connection connection) throws SQLException {
        try (var preparedStatement = connection.prepareStatement(tempTableSql)) {
            preparedStatement.execute();
        }

        log.trace("Created temp table {}", tableName);
    }

    private void upsert(Connection connection) throws SQLException {
        var startTime = System.nanoTime();

        try (PreparedStatement preparedStatement = connection.prepareStatement(upsertSql)) {
            preparedStatement.execute();
            log.debug("Upserted data from table {} to table {}", tableName, finalTableName);
        } finally {
            upsertMetric.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
