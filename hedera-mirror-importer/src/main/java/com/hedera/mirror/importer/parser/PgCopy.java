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

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;

import com.hedera.mirror.importer.converter.ByteArrayToHexSerializer;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.exception.ParserException;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 *
 * @param <T> domain object
 */
@Log4j2
public class PgCopy<T> {

    private final String sql;
    private final ObjectWriter writer;
    private final ParserProperties properties;
    protected final MeterRegistry meterRegistry;
    protected final String tableName;
    protected final Timer insertDurationMetric;

    public PgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties) {
        this(entityClass, meterRegistry, properties, entityClass.getSimpleName());
    }

    public PgCopy(Class<T> entityClass, MeterRegistry meterRegistry, ParserProperties properties, String tableName) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.tableName = CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                tableName);
        var mapper = new CsvMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(byte[].class, ByteArrayToHexSerializer.INSTANCE);
        module.addSerializer(EntityId.class, EntityIdSerializer.INSTANCE);
        mapper.registerModule(module);
        var schema = mapper.schemaFor(entityClass);
        writer = mapper.writer(schema);
        String columnsCsv = Lists.newArrayList(schema.iterator()).stream()
                .map(CsvSchema.Column::getName)
                .distinct()
                .map(name -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(", "));
        sql = String.format("COPY %s(%s) FROM STDIN WITH CSV", this.tableName, columnsCsv);
        insertDurationMetric = Timer.builder("hedera.mirror.importer.parse.insert")
                .description("Time to insert transactions into table")
                .tag("table", tableName)
                .register(meterRegistry);
    }

    public void copy(Collection<T> items, Connection connection) {
        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            setConnectionNetworkTimeout(connection);
            persistItems(items, connection);
            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows to {} table in {}", items.size(), tableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), tableName), e);
        } finally {
            clearConnectionNetworkTimeout(connection);
        }
    }

    protected void persistItems(Collection<T> items, Connection connection) throws SQLException, IOException {
        PGConnection pgConnection = connection.unwrap(PGConnection.class);
        CopyIn copyIn = pgConnection.getCopyAPI().copyIn(sql);

        try (var pgCopyOutputStream = new PGCopyOutputStream(copyIn, properties.getBufferSize())) {
            writer.writeValue(pgCopyOutputStream, items);
        } finally {
            if (copyIn.isActive()) {
                copyIn.cancelCopy();
            }
        }
    }

    private void setConnectionNetworkTimeout(Connection connection) throws SQLException {
        // PGConnection does not need an executor
        connection.setNetworkTimeout(null,
                (int) properties.getDb().getConnectionNetworkTimeout().toMillis());
    }

    private void clearConnectionNetworkTimeout(Connection connection) {
        try {
            // PGConnection does not need an executor
            connection.setNetworkTimeout(null, 0);
        } catch (SQLException e) {
            log.error("Error clearing JDBC connection network timeout:", e);
        }
    }
}

