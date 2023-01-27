package com.hedera.mirror.importer.parser.batch;

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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGenerator;

/**
 * Stateless writer to upsert rows into PostgreSQL using COPY into a temp table then insert and update into final table
 */
@Log4j2
public class BatchUpserter extends BatchInserter {

    private final String createTempTableSql;
    private final String createTempIndexSql;
    private final String finalTableName;
    private final String upsertSql;
    private final String setTempBuffersSql;
    private final String truncateSql;
    private final Timer upsertMetric;

    public BatchUpserter(Class<?> entityClass, DataSource dataSource, MeterRegistry meterRegistry,
                         CommonParserProperties properties,
                         UpsertQueryGenerator upsertQueryGenerator) {
        super(entityClass, dataSource, meterRegistry, properties, upsertQueryGenerator.getTemporaryTableName());
        createTempIndexSql = upsertQueryGenerator.getCreateTempIndexQuery();
        createTempTableSql = upsertQueryGenerator.getCreateTempTableQuery();
        setTempBuffersSql = String.format("set temp_buffers = '%dMB'", properties.getTempTableBufferSize());
        truncateSql = String.format("truncate table %s restart identity cascade", tableName);
        finalTableName = upsertQueryGenerator.getFinalTableName();
        upsertSql = upsertQueryGenerator.getUpsertQuery();
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
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(),
                    finalTableName), e);
        }
    }

    private void createTempTable(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(setTempBuffersSql)) {
            preparedStatement.executeUpdate();
        }

        // create temporary table without constraints to allow for upsert logic to determine missing data vs nulls
        try (PreparedStatement preparedStatement = connection.prepareStatement(createTempTableSql)) {
            preparedStatement.executeUpdate();
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(createTempIndexSql)) {
            preparedStatement.executeUpdate();
        }

        // ensure table is empty in case of batching
        try (PreparedStatement preparedStatement = connection.prepareStatement(truncateSql)) {
            preparedStatement.executeUpdate();
        }

        log.trace("Created temp table {}", tableName);
    }

    private void upsert(Connection connection) throws SQLException {
        var startTime = System.nanoTime();

        try (PreparedStatement preparedStatement = connection.prepareStatement(upsertSql)) {
            int count = preparedStatement.executeUpdate();
            log.debug("Inserted {} rows from {} table to {} table", count, tableName, finalTableName);
        } finally {
            upsertMetric.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}

