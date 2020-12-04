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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.db.DBProperties;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class FlywayConfiguration {

    private final ApplicationContext applicationContext;
    private final DataSource dataSource;
    private final DBProperties dbProperties;
    private final MirrorProperties mirrorProperties;

    @Bean
    FlywayMigrationInitializer flywayInitializer() {
        Map<String, String> placeholders = Map.of(
                "db-name", dbProperties.getName(),
                "db-user", dbProperties.getUsername(),
                "api-user", dbProperties.getRestUsername(),
                "api-password", dbProperties.getRestPassword(),
                "topicRunningHashV2AddedTimestamp", getTopicRunningHashV2AddedTimestamp()
        );

        FluentConfiguration config = Flyway.configure()
                .baselineDescription("Mirror Node DB Baseline")
                .baselineOnMigrate(dbProperties.getFlywayProperties().isBaselineOnMigrate())
                .baselineVersion(dbProperties.getFlywayProperties().getBaselineVersion())
                .connectRetries(dbProperties.getFlywayProperties().getConnectRetries())
                .dataSource(dataSource)
                .ignoreMissingMigrations(dbProperties.getFlywayProperties().isIgnoreMissingMigrations())
                .placeholders(placeholders)
                .target(dbProperties.getFlywayProperties().getTarget())
                .javaMigrations(applicationContext.getBeansOfType(JavaMigration.class).values()
                        .toArray(new JavaMigration[0]));
        Flyway flyway = config.load();
        return new FlywayMigrationInitializer(flyway, (f) -> {
            if (!isFlywayInitialized()) {
                log.info("Baselining migration to {}", flyway.getConfiguration().getBaselineVersion());
                flyway.baseline();
            }

            log.info("Initiating flyway migration with placeholders: {}", flyway.getConfiguration().getPlaceholders());
            flyway.migrate();
        });
    }

    private boolean isFlywayInitialized() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet result = metadata.getTables(null, null, "flyway_schema_history", null);
            return result.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if Flyway is initialized", e);
        }
    }

    private String getTopicRunningHashV2AddedTimestamp() {
        Long timestamp = mirrorProperties.getTopicRunningHashV2AddedTimestamp();
        if (timestamp == null) {
            if (mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.MAINNET) {
                timestamp = 1592499600000000000L;
            } else {
                timestamp = 1588706343553042000L;
            }
        }

        return timestamp.toString();
    }
}
