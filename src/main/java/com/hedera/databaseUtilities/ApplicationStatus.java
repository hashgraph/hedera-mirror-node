package com.hedera.databaseUtilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ApplicationStatus {

	public enum ApplicationStatusCode {
		LVDRF		// Last valid downloaded record file name
		,LVDRFH		// Last valid downloaded record file hash
		,LVDBF		// Last valid downloaded balance file name
		,LVDEF		// Last valid downloaded event file name
		,LVDEFH		// Last valid downloaded event file hash
		,EHMBUA		// Event hash mismatch bypass until after
		,RHMBUA		// Record hash mismatch bypass until after
		,LPEH		// Last processed record hash
		,LPRH		// Last processed event hash
	}
	
	public static boolean setStatus(ApplicationStatusCode code, String statusValue) {
		
	    try (Connection connect = DatabaseUtilities.getConnection()) {
	    	log.debug("Updating application status for : {}", code.name());
	
	    	try {
				PreparedStatement updateValue = connect.prepareStatement(
						"UPDATE t_application_status SET "
						+ " status_value = ? "
						+ " WHERE status_code = ?");
		
				updateValue.setString(1, statusValue);
				updateValue.setString(2, code.name());
				
				updateValue.execute();
				updateValue.close();
	
				return true;
			} catch (Exception e) {
				log.error("Error updating application status for : {}, {}", code.name(), e);
			}
	    } catch (Exception e) {
	        log.error("Error connecting to database", e);
	    }
	    return false;
	}

	public static String getStatus(ApplicationStatusCode code) {
		String value = "";
	    try (Connection connect = DatabaseUtilities.getConnection()) {
	    	log.debug("Getting application status for : {}", code.name());
	
	    	try {
				PreparedStatement getValue = connect.prepareStatement(
						"SELECT status_value FROM t_application_status "
						+ " WHERE status_code = ?");
		
				getValue.setString(1, code.name());
				getValue.execute();
				ResultSet appStatus = getValue.getResultSet();

				if (appStatus.next()) {
					value = appStatus.getString(1);
					if (value == null) { value = "";}
				} else {
					log.error("Application status code {} does not exist in the database", code.name());
				}
				appStatus.close();
				getValue.close();
			} catch (Exception e) {
				log.error("Error getting application status for : {}, {}", code.name(), e);
			}
	    } catch (Exception e) {
	        log.error("Error connecting to database", e);
	    }
	    return value;
	}
}
