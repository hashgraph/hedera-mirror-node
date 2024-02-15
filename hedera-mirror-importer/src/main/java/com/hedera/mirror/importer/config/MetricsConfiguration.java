/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Configuration
@RequiredArgsConstructor
class MetricsConfiguration {
    private static final String METRIC_SQL =
            """
                    select
                        coalesce(sum(pg_table_size(psu.relid)), 0) as table_size,
                        coalesce(sum(pg_indexes_size(psu.relid)), 0) as index_size,
                        coalesce(sum(pc.reltuples::bigint), 0) as rows
                    from pg_catalog.pg_statio_user_tables psu
                       join pg_class pc on psu.relname = pc.relname
                       join pg_database pd on pc.relowner = pd.datdba
                       left join pg_inherits pi on pi.inhrelid = pc.oid
                    where pd.datname = '%s' and %s
                    group by pi.inhparent
                    """;
    private static final String DISTRIBUTED_METRIC_SQL =
            """
                    with shard_data as (
                            select (string_to_array(substring(result, 2, length(result) - 2), ',')) as stats
                            from run_command_on_shards(?, $cmd$
                                select
                                    row(coalesce(sum(pg_table_size(psu.relid)), 0),
                                    coalesce(sum(pg_indexes_size(psu.relid)), 0),
                                    coalesce(sum(pc.reltuples::bigint), 0))
                                    from pg_catalog.pg_statio_user_tables psu
                                       join pg_class pc on psu.relname = pc.relname
                                       join pg_database pd on pc.relowner = pd.datdba
                                       left join pg_inherits pi on pi.inhrelid = pc.oid
                                    where pd.datname = '%s' and %s
                                    group by pi.inhparent
                              $cmd$))
                    select sum(stats[1]::float::bigint) as table_size,
                           sum(stats[2]::float::bigint) as index_size,
                           sum(stats[3]::float::bigint) as rows
                    from shard_data;
                    """;

    private final DataSource dataSource;
    private final DBProperties dbProperties;
    private final @Lazy JdbcOperations jdbcOperations;
    private final Map<String, TableMetrics> activeMetrics = new ConcurrentHashMap<>();
    private final List<TableAttributes> tables = new ArrayList<>();

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
    MeterBinder tableMetrics(Environment environment) {
        return registry -> {
            tables.addAll(getTables(environment.acceptsProfiles(Profiles.of("v2"))));
            tables.forEach(v -> registerTableMetrics(registry, v));
        };
    }

    @Scheduled(fixedDelayString = "${hedera.mirror.importer.db.metrics.refreshInterval:300000}")
    public void updateTableMetrics() {
        for (var table : tables) {
            var sql = getMetricSql(table);
            try {
                var result = jdbcOperations.queryForObject(
                        sql, DataClassRowMapper.newInstance(TableMetrics.class), table.tableName());
                activeMetrics.put(table.tableName(), result);
            } catch (BadSqlGrammarException e) {
                // ignore as table may have been removed by a migration
            } catch (Exception e) {
                log.warn("Unable to get metrics for table {}", table.tableName(), e);
            }
        }

        log.info("Updated table metrics");
    }

    private Map<String, Boolean> getDistributedTables() {
        String sql = "SELECT table_name from citus_tables";
        return jdbcOperations.queryForList(sql, String.class).stream()
                .collect(Collectors.toMap(Function.identity(), t -> true));
    }

    private void registerTableMetrics(MeterRegistry registry, TableAttributes tableAttributes) {
        for (TableMetric tableMetric : TableMetric.values()) {
            ToDoubleFunction<DataSource> func =
                    ds -> Optional.ofNullable(activeMetrics.get(tableAttributes.tableName()))
                            .map(tableMetric.valueFunction)
                            .orElse(0L);

            Gauge.builder(tableMetric.metricName, dataSource, func)
                    .tag("database", dbProperties.getName())
                    .tag("schema", dbProperties.getSchema())
                    .tag("table", tableAttributes.tableName())
                    .description(tableMetric.description)
                    .baseUnit(tableMetric.baseUnits)
                    .register(registry);
        }
    }

    private String getMetricSql(TableAttributes table) {
        var metricSql = "";
        var tableClause = table.partitioned() ? "pi.inhparent::regclass = ?::regclass " : "pc.relname = ?";
        if (table.distributed()) {
            metricSql = DISTRIBUTED_METRIC_SQL;
            tableClause = tableClause.replace("?", "'%s'");
        } else {
            metricSql = METRIC_SQL;
        }
        return String.format(metricSql, dbProperties.getName(), tableClause);
    }

    // Get a list of tables and their attributes
    private Collection<TableAttributes> getTables(boolean isV2) {
        Map<String, Boolean> distributedTables = isV2 ? getDistributedTables() : new HashMap<>();
        Map<String, TableAttributes> tableMap = new HashMap<>();
        var types = new String[] {"TABLE"};
        try (var connection = dataSource.getConnection();
                var rs = connection.getMetaData().getTables(null, dbProperties.getSchema(), null, types)) {
            var partitionedPattern = Pattern.compile("^(.+?)(_p\\d+|_p\\d+_\\d+|_\\d+|_default)$");
            while (rs.next()) {
                var tableName = rs.getString("TABLE_NAME");
                var parentTable = tableName;
                var matcher = partitionedPattern.matcher(tableName);

                if (matcher.matches()) {
                    parentTable = matcher.group(1);
                }

                var partitioned = !parentTable.equals(tableName)
                        || Optional.ofNullable(tableMap.get(tableName))
                                .map(TableAttributes::partitioned)
                                .orElse(false);

                tableMap.put(
                        parentTable,
                        new TableAttributes(parentTable, distributedTables.containsKey(parentTable), partitioned));
            }
        } catch (Exception e) {
            log.warn("Unable to list table names. No table metrics will be available", e);
        }

        log.info("Collecting {} table metrics: {}", tableMap.size(), tableMap.keySet());

        return tableMap.values();
    }

    @Getter
    @RequiredArgsConstructor
    enum TableMetric {
        INDEX_BYTES(BaseUnits.BYTES, "db.index.bytes", "The size of the indexes on disk", TableMetrics::indexSize),
        TABLE_BYTES(BaseUnits.BYTES, "db.table.bytes", "The size of the table on disk", TableMetrics::tableSize),
        TABLE_SIZE(BaseUnits.ROWS, "db.table.rows", "The number of rows in the table", TableMetrics::rows);

        private final String baseUnits;
        private final String metricName;
        private final String description;

        private final Function<TableMetrics, Long> valueFunction;
    }

    private record TableAttributes(String tableName, boolean distributed, boolean partitioned) {}

    private record TableMetrics(Long tableSize, Long indexSize, Long rows) {}
}
