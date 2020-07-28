package com.hedera.mirror.importer.parser.record.entity.sql;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import com.hedera.mirror.importer.converter.ByteArrayToHexSerializer;
import com.hedera.mirror.importer.exception.ParserException;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 *
 * @param <T> domain object
 */
@Log4j2
public class PgCopy<T> {

    private final String tableName;
    private final String sql;
    private final ObjectWriter writer;
    private final Timer buildCsvDurationMetric;
    private final Timer insertDurationMetric;
    private final SqlProperties properties;

    public PgCopy(Class<T> entityClass, MeterRegistry meterRegistry, SqlProperties properties) {
        this.properties = properties;
        tableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entityClass.getSimpleName());
        var mapper = new CsvMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(byte[].class, new ByteArrayToHexSerializer());
        mapper.registerModule(module);
        var schema = mapper.schemaFor(entityClass);
        writer = mapper.writer(schema);
        String columnsCsv = Lists.newArrayList(schema.iterator()).stream()
                .map(CsvSchema.Column::getName)
                .map(name -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(", "));
        sql = String.format("COPY %s(%s) FROM STDIN WITH CSV", tableName, columnsCsv);

        buildCsvDurationMetric = Timer.builder("hedera.mirror.importer.parse.csv")
                .description("Time to build csv string")
                .tag("table", tableName)
                .register(meterRegistry);
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
            var reader = buildCsv(items);
            Stopwatch stopwatch = Stopwatch.createStarted();
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();

            long rowsCount = copyManager.copyIn(sql, reader, properties.getBufferSize());
            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows to {} table in {}", rowsCount, tableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    private Reader buildCsv(Collection<T> items) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String csv = writer.writeValueAsString(items);
        buildCsvDurationMetric.record(stopwatch.elapsed());
        log.trace("CSV for {} of size {} generated in {}", tableName, csv.length(), stopwatch);
        return new StringReader(csv);
    }
}

