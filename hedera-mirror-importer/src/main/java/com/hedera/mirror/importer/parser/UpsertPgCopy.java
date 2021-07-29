package com.hedera.mirror.importer.parser;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGenerator;

/**
 * Stateless writer to upsert rows into PostgreSQL using COPY into a temp table then insert and update into final table
 *
 * @param <T> domain object
 */
@Log4j2
public class UpsertPgCopy<T> extends PgCopy<T> {
    private static final String TABLE = "table";
    private final String createTempTableSql;
    private final String finalTableName;
    private final String insertSql;
    private final String updateSql;
    private final Timer copyDurationMetric;
    private final Timer finalInsertDurationMetric;
    private final Timer updateDurationMetric;
    private final String truncateSql;

    public UpsertPgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties,
                        UpsertQueryGenerator upsertQueryGenerator) {
        super(entityClass, meterRegistry, properties, upsertQueryGenerator.getTemporaryTableName());
        createTempTableSql = upsertQueryGenerator.getCreateTempTableQuery();
        truncateSql = String
                .format("truncate table %s cascade", upsertQueryGenerator.getTemporaryTableName());
        finalTableName = upsertQueryGenerator.getFinalTableName();
        insertSql = upsertQueryGenerator.getInsertQuery();
        updateSql = upsertQueryGenerator.getUpdateQuery();
        copyDurationMetric = Timer.builder("hedera.mirror.importer.parse.upsert.copy")
                .description("Time to copy transaction information from importer to temp table")
                .tag(TABLE, upsertQueryGenerator.getTemporaryTableName())
                .register(meterRegistry);
        finalInsertDurationMetric = Timer.builder("hedera.mirror.importer.parse.upsert.insert")
                .description("Time to insert transaction information from temp to final table")
                .tag(TABLE, finalTableName)
                .register(meterRegistry);
        updateDurationMetric = Timer.builder("hedera.mirror.importer.parse.upsert.update")
                .description("Time to update parsed transactions information into table")
                .tag(TABLE, finalTableName)
                .register(meterRegistry);
    }

    @Override
    public void init(Connection connection) {
        try {
            createTempTable(connection);
        } catch (Exception e) {
            throw new ParserException(String.format("Error initializing table %s", tableName), e);
        }
    }

    @Override
    protected void persistItems(Collection<T> items, Connection connection) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        try {
            // copy items to temp table
            copyItems(items, connection);

            // insert items from temp table to final table
            int insertCount = insertItems(connection);

            // update items in final table from temp table
            updateItems(connection);

            log.debug("Inserted {} and updated from a total of {} rows to {}", insertCount, items
                    .size(), finalTableName);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), tableName), e);
        }
    }

    private int insertToFinalTable(Connection connection) throws SQLException {
        int insertCount = 0;
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {
            insertCount = preparedStatement.executeUpdate();
        }
        log.trace("Inserted {} rows from {} table to {} table", insertCount, tableName, finalTableName);
        return insertCount;
    }

    private void updateFinalTable(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
            preparedStatement.execute();
        }
        log.trace("Updated rows from {} table to {} table", tableName, finalTableName);
    }

    private void createTempTable(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection
                .prepareStatement("SET experimental_enable_temp_tables = 'on'")) {
            preparedStatement.execute();
        }

        // create temporary table without constraints to allow for upsert logic to determine missing data vs nulls
        try (PreparedStatement preparedStatement = connection.prepareStatement(createTempTableSql)) {
            preparedStatement.executeUpdate();
        }

        // ensure table is empty in case of batching
        try (PreparedStatement preparedStatement = connection.prepareStatement(truncateSql)) {
            preparedStatement.executeUpdate();
        }
        log.trace("Created temp table {}", tableName);
    }

    private void copyItems(Collection<T> items, Connection connection) throws SQLException, IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        super.persistItems(items, connection);
        recordMetric(copyDurationMetric, stopwatch);
    }

    private int insertItems(Connection connection) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int insertCount = insertToFinalTable(connection);
        recordMetric(finalInsertDurationMetric, stopwatch);
        return insertCount;
    }

    private void updateItems(Connection connection) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        updateFinalTable(connection);
        recordMetric(updateDurationMetric, stopwatch);
    }

    private void recordMetric(Timer timer, Stopwatch stopwatch) {
        timer.record(stopwatch.elapsed());
    }
}

