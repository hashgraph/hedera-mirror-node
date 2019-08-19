package com.hedera.databaseUtilities;

import com.google.common.util.concurrent.Uninterruptibles;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.utilities.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.concurrent.TimeUnit;

public class DatabaseUtilities {
    private static final Logger log = LogManager.getLogger(DatabaseUtilities.class);

    public static Connection openDatabase(Connection connect) {
        if (connect == null) {
            while (true) {
                try {
                    if (Utility.checkStopFile()) {
                        log.info("Stop file found, stopping.");
                        break;
                    }

                    // Setup the connection with the DB
                    String url = ConfigLoader.getDBUrl();
                    String userName = ConfigLoader.getDBUserName();
                    String password = ConfigLoader.getDBPassword();

                    return DriverManager.getConnection(url, userName, password);
                } catch (SQLException e) {
                    log.warn("Unable to connect to database. Will retry in 3s: {}", e.getMessage());
                }
                Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
            }
        }
        return connect;
    }

    public static Connection closeDatabase(Connection connect) throws SQLException {
        connect.close();
        return null;
    }
}
