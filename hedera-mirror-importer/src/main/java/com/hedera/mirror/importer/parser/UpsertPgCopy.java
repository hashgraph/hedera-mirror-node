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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.repository.UpdatableDomainRepositoryCustom;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 *
 * @param <T> domain object
 */
@Log4j2
public class UpsertPgCopy<T> extends PgCopy<T> {
    private final String finalTableName;
    private final String createTempTableSql;
    private final String insertSql;
    private final String updateSql;

    public UpsertPgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties,
                        UpdatableDomainRepositoryCustom updatableDomainRepositoryCustom) {
        super(entityClass, meterRegistry, properties, updatableDomainRepositoryCustom.getTemporaryTableName());
        createTempTableSql = updatableDomainRepositoryCustom.getCreateTempTableQuery();
        finalTableName = updatableDomainRepositoryCustom.getTableName();
        insertSql = updatableDomainRepositoryCustom.getInsertQuery();
        updateSql = updatableDomainRepositoryCustom.getUpdateQuery();

        // flag that this is not the base insert copy scenario
        insertCopy = false;
    }

    @Override
    public void copy(Collection<T> items, Connection connection) throws SQLException {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        // create temp table to copy into
        createTempTable(connection);

        Stopwatch stopwatch = Stopwatch.createStarted();
        // copy items to temp table
        super.copy(items, connection);

        // from temp table upsert to final table
        int insertCount = insertToFinalTable(connection);
        updateFinalTable(connection);
        insertDurationMetric.record(stopwatch.elapsed());
        log.info("Inserted {} and updated from a total of {} rows to {} in {}", insertCount, items
                .size(), finalTableName, stopwatch);
    }

    private void createTempTable(Connection connection) throws SQLException {
        // create temporary table without constraints to allow for upsert logic to determine missing data vs nulls
        Stopwatch stopwatch = Stopwatch.createStarted();
        try (PreparedStatement preparedStatement = connection.prepareStatement(createTempTableSql)) {
            preparedStatement.executeUpdate();
        }
        log.trace("Created temp table {} in {}", tableName, stopwatch);
    }

    public int insertToFinalTable(Connection connection) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int insertCount = 0;
        log.info("** Run insertSql {}", insertSql);
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {
            insertCount = preparedStatement.executeUpdate();
        }
        log.info("Inserted {} rows from {} table to {} table in {}", insertCount, tableName, finalTableName,
                stopwatch);
        return insertCount;
    }

    public void updateFinalTable(Connection connection) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        log.info("** Run updateSql {}", updateSql);
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
            preparedStatement.execute();
        }
        log.info("Updated rows from {} table to {} table in {}", tableName, finalTableName, stopwatch);
    }
}

