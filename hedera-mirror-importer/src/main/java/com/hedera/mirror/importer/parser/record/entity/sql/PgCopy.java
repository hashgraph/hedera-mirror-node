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
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.output.StringBuilderWriter;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;

import com.hedera.mirror.importer.exception.ParserException;

// TODO: unit tests
/**
 * Stateless writer to insert rows into Postgres table using COPY.
 * @param <T> domain object
 */
@Log4j2
public class PgCopy<T> implements Closeable {
    private final Connection connection;
    private final String tableName;
    private final String columnsCsv;
    private final ObjectWriter writer;
    private final CopyManager copyManager;

    public PgCopy(Connection connection, Class<T> tClass) throws SQLException {
        this.connection = connection;
        this.copyManager = connection.unwrap(PgConnection.class).getCopyAPI();
        this.tableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, tClass.getSimpleName());
        var mapper = new CsvMapper();
        var schema = mapper.schemaFor(tClass);
        this.writer = mapper.writer(schema);
        this.columnsCsv = Lists.newArrayList(schema.iterator()).stream()
                .map(CsvSchema.Column::getName)
                .map(name -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(", "));
    }

    public void copy(List<T> items) {
        if (items.size() == 0) {
            return;
        }
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            long rowsCount = copyManager.copyIn(
                    String.format("COPY %s(%s) FROM STDIN WITH CSV", tableName, columnsCsv),
                    new StringReader(getCsvData(items)));
            log.info("Copied {} rows to {} table in {}ms",
                    rowsCount, tableName, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (IOException | SQLException e) {
            throw new ParserException(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.error("Exception closing connection", e);
        }
    }

    public String getCsvData(List<T> items) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        var stringBuilderWriter = new StringBuilderWriter();
        writer.writeValues(stringBuilderWriter).writeAll(items);
        var csvData = stringBuilderWriter.getBuilder().toString();
        log.debug("csv string length={} time={}ms", csvData.length(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return csvData;
    }
}

