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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

@Log4j2
@Configuration
@RequiredArgsConstructor
class MetricsConfiguration {

    private final DataSource dataSource;
    private final DBProperties dbProperties;

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
    MeterBinder tableMetrics(@Lazy JdbcOperations jdbcOperations) {
        return registry -> getTablesNames().forEach(t -> registerTableMetric(jdbcOperations, registry, t));
    }

    // select count(*) is very slow on large tables, so we use the stats table to provide an estimate
    private void registerTableMetric(JdbcOperations jdbcOperations, MeterRegistry registry, String tableName) {
        final String query = "select n_live_tup from pg_stat_all_tables where schemaname = ? and relname = ?";
        ToDoubleFunction<DataSource> totalRows =
                ds -> jdbcOperations.queryForObject(query, Long.class, dbProperties.getSchema(), tableName);

        Gauge.builder("db.table.size", dataSource, totalRows)
                .tag("db", dbProperties.getName())
                .tag("table", tableName)
                .description("Number of rows in a database table")
                .baseUnit(BaseUnits.ROWS)
                .register(registry);
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
}
