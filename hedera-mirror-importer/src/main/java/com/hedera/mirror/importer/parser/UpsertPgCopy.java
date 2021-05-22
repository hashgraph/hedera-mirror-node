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

import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.extern.log4j.Log4j2;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 *
 * @param <T> domain object
 */
@Log4j2
public class UpsertPgCopy<T> extends PgCopy<T> {

    public static final String TEMP_POSTFIX = "_temp";
    private final String finalTableName;
    private final String upsertSql;

    public UpsertPgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties,
                        String tempTableName, String upsertSql) {
        super(entityClass, meterRegistry, properties);
        tableName = CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                tempTableName);
        finalTableName = CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                entityClass.getSimpleName());
        this.upsertSql = upsertSql;
    }

    public void createTempTable(Connection connection) throws SQLException {
        // create temporary table without constraints to allow for upsert logic to determine missing data vs nulls
        connection.prepareStatement(
                String.format("create temporary table %s on commit drop as table %s limit 0", tableName,
                        finalTableName))
                .executeUpdate();
        log.trace("Created temp table {}", tableName);
    }

    public int upsertFinalTable(Connection connection) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int updateCount = connection.prepareStatement(upsertSql).executeUpdate();
        log.info("Copied {} rows from {} table to {} table in {}", updateCount, tableName, finalTableName, stopwatch);
        return updateCount;
    }
}

