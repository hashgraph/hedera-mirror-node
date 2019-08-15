package com.hedera.databaseUtilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.configLoader.ConfigLoader;

public class DatabaseUtilities {
    private static final Logger log = LogManager.getLogger("databaseUtilities");
    static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

    public static Connection openDatabase(Connection connect) {
        if (connect == null) {
            while (true) {
                try {
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
