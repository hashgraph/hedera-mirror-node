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

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
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
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.converter.ByteArrayToHexSerializer;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 */
@Log4j2
public class BatchInserter implements BatchPersister {

    protected final DataSource dataSource;
    protected final MeterRegistry meterRegistry;
    protected final String tableName;
    protected final Timer insertDurationMetric;
    private final String sql;
    private final ObjectWriter writer;
    private final CommonParserProperties properties;

    public BatchInserter(Class<?> entityClass, DataSource dataSource, MeterRegistry meterRegistry,
                         CommonParserProperties properties) {
        this(entityClass, dataSource, meterRegistry, properties, entityClass.getSimpleName());
    }

    public BatchInserter(Class<?> entityClass, DataSource dataSource, MeterRegistry meterRegistry,
                         CommonParserProperties properties, String tableName) {
        this.dataSource = dataSource;
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
        mapper.configure(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS, true);
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
                .tag("table", this.tableName)
                .register(meterRegistry);
    }

    @Override
    public void persist(Collection<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            persistItems(items, connection);
            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows to {} table in {}", items.size(), tableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), tableName), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    protected void persistItems(Collection<?> items, Connection connection) throws SQLException, IOException {
        PGConnection pgConnection = connection.unwrap(PGConnection.class);
        CopyIn copyIn = pgConnection.getCopyAPI().copyIn(sql);

        if (log.isTraceEnabled()) {
            String csv = writer.writeValueAsString(items);
            log.trace("Generated SQL: {}\n{}", sql, csv);
        }

        try (var pgCopyOutputStream = new PGCopyOutputStream(copyIn, properties.getBufferSize())) {
            writer.writeValue(pgCopyOutputStream, items);
        } finally {
            if (copyIn.isActive()) {
                copyIn.cancelCopy();
            }
        }
    }
}
