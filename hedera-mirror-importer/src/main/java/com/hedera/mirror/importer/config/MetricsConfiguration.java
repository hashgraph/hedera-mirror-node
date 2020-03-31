package com.hedera.mirror.importer.config;

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

import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.db.DatabaseTableMetrics;
import io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class MetricsConfiguration {

    private final DataSource dataSource;
    private final DataSourceProperties dataSourceProperties;

    @Bean
    public MeterBinder processMemoryMetrics() {
        return new ProcessMemoryMetrics();
    }

    @Bean
    public MeterBinder processThreadMetrics() {
        return new ProcessThreadMetrics();
    }

    @Bean
    MeterBinder postgreSQLDatabaseMetrics() {
        return new PostgreSQLDatabaseMetrics(dataSource, dataSourceProperties.getName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "management.metrics.table", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    MeterBinder tableMetrics() {
        // select count(*) is very slow on large tables, so we use the stats table to provide an estimate
        final String query = "select n_live_tup from pg_stat_all_tables where relname = '%s'";
        String name = dataSourceProperties.getName();

        return registry -> getTablesNames().stream()
                .map(table -> new DatabaseTableMetrics(dataSource, String.format(query, table), name, table, null))
                .forEach(mb -> mb.bindTo(registry));
    }

    private Collection<String> getTablesNames() {
        Collection<String> tableNames = new LinkedHashSet<>();

        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {

            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            log.warn("Unable to list table names. No table metrics will be available", e);
        }

        log.info("Collecting table metrics: {}", tableNames);
        return tableNames;
    }
}
