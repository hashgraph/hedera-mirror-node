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

import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @deprecated Use Spring Data repositories with domain objects
 */
@Deprecated(forRemoval = true, since = "v0.3.0")
@Log4j2
@Named
public class DatabaseUtilities {

    private static volatile DataSource dataSource;

    // Temporary hack until we remove this class and migrate to repositories
    DatabaseUtilities(DataSource dataSource) {
        DatabaseUtilities.dataSource = dataSource;
    }

    public static final Connection getConnection() {
        while (true) {
            try {
                if (Utility.checkStopFile()) {
                    log.info("Stop file found, stopping.");
                    System.exit(0);
                }

                return dataSource.getConnection();
            } catch (Exception e) {
                log.warn("Unable to connect to database: {}", e.getMessage());
            }
        }
    }

    public static Connection openDatabase(Connection connect) {
        return getConnection();
    }

    public static Connection closeDatabase(Connection connect) throws SQLException {
        connect.close();
        return null;
    }
}
