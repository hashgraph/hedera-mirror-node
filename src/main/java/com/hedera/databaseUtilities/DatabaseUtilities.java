package com.hedera.databaseUtilities;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.common.collect.ImmutableMap;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.utilities.Utility;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import java.sql.Connection;
import java.sql.SQLException;

@Log4j2
public class DatabaseUtilities {

    private static final HikariDataSource dataSource;
    private static volatile boolean migrated = false;

    static {
        dataSource = new HikariDataSource();
        dataSource.setConnectionTimeout(3000L);
        dataSource.setInitializationFailTimeout(-1L);
        dataSource.setJdbcUrl(ConfigLoader.getDBUrl());
        dataSource.setPassword(ConfigLoader.getDBPassword());
        dataSource.setUsername(ConfigLoader.getDBUserName());
    }

    // No synchronization since it's okay to run concurrently as flyway will lock tables anyway
    public static final void migrate() {
        if (!migrated) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))

                    // Allow missing migrations without failing the overall migration.
                    // This would allow the database to have a "V1.23" migration in it (before the product is 1.0-ready),
                    // that gets removed by developers at a future point.
                    // When the software is updated and no longer has V1.23 in it (bad migration file removed by dev),
                    // but flyway sees a database that had V1.23 in it - ignore this issue and continue flyway migrations.
                    // This allows development to more easily "fix" some bad initial migration files without resorting
                    // to `flyway repair`.
                    .ignoreMissingMigrations(true)

                    .placeholders(ImmutableMap.of(
                            "api-user", ConfigLoader.getApiUsername(),
                            "api-password", ConfigLoader.getApiPassword(),
                            "db-name", ConfigLoader.getDBName(),
                            "db-user", ConfigLoader.getDBUserName()))
                    .load()
                    .migrate();
            migrated = true;
        }
    }

    public static final Connection getConnection() {
        while (true) {
            try {
                if (Utility.checkStopFile()) {
                    log.info("Stop file found, stopping.");
                    System.exit(0);
                }

                Connection connection = dataSource.getConnection();
                migrate();
                return connection;
            } catch (Exception e) {
                log.warn("Unable to connect to database. Will retry in {}ms: {}", dataSource.getConnectionTimeout(), e.getMessage());
            }
        }
    }

    /*
     * @deprecated Use getConnection()
     */
    @Deprecated(forRemoval = true)
    public static Connection openDatabase(Connection connect) {
        return getConnection();
    }

    /*
     * @deprecated Use try-with-resources
     */
    @Deprecated(forRemoval = true)
    public static Connection closeDatabase(Connection connect) throws SQLException {
        connect.close();
        return null;
    }
}
