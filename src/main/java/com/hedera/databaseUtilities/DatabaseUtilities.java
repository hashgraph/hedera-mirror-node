package com.hedera.databaseUtilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
            try {
            	ConfigLoader configLoader = new ConfigLoader();
                // Setup the connection with the DB
                String url = configLoader.getDBUrl();
                String userName = configLoader.getDBUserName();
                String password = configLoader.getDBPassword();
                
                connect = DriverManager.getConnection(url, userName, password);
            } catch (SQLException e) {
                e.printStackTrace();
                log.error(LOGM_EXCEPTION, "Exception {}", e);
                return null;
            }
        }
        return connect;
    }

    public static Connection closeDatabase(Connection connect) throws SQLException {
        connect.close();
        return null;
    }
}
