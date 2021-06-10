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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.UpdatableDomainRepositoryCustom;

/**
 * Stateless writer to upsert rows into PostgreSQL using COPY into a temp table then insert and update into final table
 *
 * @param <T> domain object
 */
@Log4j2
public class UpsertPgCopy<T> extends PgCopy<T> {
    private final String finalTableName;
    private final String createTempTableSql;
    private final String insertSql;
    private final String updateSql;

//    protected Timer copyDurationMetric;

    public UpsertPgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties,
                        UpdatableDomainRepositoryCustom updatableDomainRepositoryCustom) {
        super(entityClass, meterRegistry, properties, updatableDomainRepositoryCustom.getTemporaryTableName());
        createTempTableSql = updatableDomainRepositoryCustom.getCreateTempTableQuery();
        finalTableName = updatableDomainRepositoryCustom.getTableName();
        insertSql = updatableDomainRepositoryCustom.getInsertQuery();
        updateSql = updatableDomainRepositoryCustom.getUpdateQuery();
        insertDurationMetric = Timer.builder("hedera.mirror.importer.parse.insert")
                .description("Time to insert parsed transactions information into table")
                .tag("table", finalTableName)
                .register(meterRegistry);
    }

    @Override
    public void copy(Collection<T> items, Connection connection) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        try {
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
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), tableName), e);
        }
    }

    @Override
    protected Timer getCopyDurationMetric() {
        return null;
    }

    private void createTempTable(Connection connection) throws SQLException {
        // create temporary table without constraints to allow for upsert logic to determine missing data vs nulls
        try (PreparedStatement preparedStatement = connection.prepareStatement(createTempTableSql)) {
            preparedStatement.executeUpdate();
        }
        log.trace("Created temp table {}", tableName);
    }

    public int insertToFinalTable(Connection connection) throws SQLException {
        int insertCount = 0;
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {
            insertCount = preparedStatement.executeUpdate();
        }
        log.trace("Inserted {} rows from {} table to {} table", insertCount, tableName, finalTableName);
        return insertCount;
    }

    public void updateFinalTable(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
            preparedStatement.execute();
        }
        log.trace("Updated rows from {} table to {} table", tableName, finalTableName);
    }
}

