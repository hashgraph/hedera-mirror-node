/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.db.DBProperties;
import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.ToDoubleFunction;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

@CustomLog
@Configuration
@RequiredArgsConstructor
class MetricsConfiguration {

    private final DataSource dataSource;
    private final DBProperties dbProperties;
    private final @Lazy JdbcOperations jdbcOperations;

    @Bean
    MeterBinder processMemoryMetrics() {
        return new ProcessMemoryMetrics();
    }

    @Bean
    MeterBinder processThreadMetrics() {
        return new ProcessThreadMetrics();
    }

    @Bean
    MeterBinder postgreSQLDatabaseMetrics() {
        return new PostgreSQLDatabaseMetrics(dataSource, dbProperties.getName());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "management.metrics.table",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    MeterBinder tableMetrics() {
        return registry -> getTablesNames().forEach(t -> registerTableMetrics(registry, t));
    }

    private void registerTableMetrics(MeterRegistry registry, String tableName) {
        for (TableMetric tableMetric : TableMetric.values()) {
            ToDoubleFunction<DataSource> func = ds ->
                    jdbcOperations.queryForObject(tableMetric.query, Long.class, dbProperties.getSchema(), tableName);
            Gauge.builder(tableMetric.metricName, dataSource, func)
                    .tag("database", dbProperties.getName())
                    .tag("schema", dbProperties.getSchema())
                    .tag("table", tableName)
                    .description(tableMetric.description)
                    .baseUnit(tableMetric.baseUnits)
                    .register(registry);
        }
    }

    private Collection<String> getTablesNames() {
        Collection<String> tableNames = new LinkedHashSet<>();
        var types = new String[] {"TABLE"};

        try (var connection = dataSource.getConnection();
                var rs = connection.getMetaData().getTables(null, dbProperties.getSchema(), null, types)) {

            while (rs.next()) {
                var tableName = rs.getString("TABLE_NAME");
                if (!tableName.endsWith("_default") && !tableName.matches(".*\\d+$")) {
                    tableNames.add(tableName);
                }
            }
        } catch (Exception e) {
            log.warn("Unable to list table names. No table metrics will be available", e);
        }

        log.info("Collecting {} table metrics: {}", tableNames.size(), tableNames);
        return tableNames;
    }

    @RequiredArgsConstructor
    private enum TableMetric {
        INDEX_BYTES(
                BaseUnits.BYTES,
                "db.index.bytes",
                "The size of the indexes on disk",
                "select pg_indexes_size('\"' || ? || '\".\"' || ? || '\"')"),
        TABLE_BYTES(
                BaseUnits.BYTES,
                "db.table.bytes",
                "The size of the table on disk",
                "select pg_table_size('\"' || ? || '\".\"' || ? || '\"')"),
        TABLE_SIZE(
                BaseUnits.ROWS,
                "db.table.rows",
                "The number of rows in the table",
                "select n_live_tup from pg_stat_all_tables where schemaname = ? and relname = ?");

        private final String baseUnits;
        private final String metricName;
        private final String description;
        private final String query;
    }
}
