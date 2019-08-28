package com.hedera.databaseutilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ApplicationStatus {

	public enum ApplicationStatusCode {
		LAST_VALID_DOWNLOADED_RECORD_FILE
		,LAST_VALID_DOWNLOADED_RECORD_FILE_HASH		
		,LAST_VALID_DOWNLOADED_BALANCE_FILE
		,LAST_VALID_DOWNLOADED_EVENT_FILE
		,LAST_VALID_DOWNLOADED_EVENT_FILE_HASH
		,EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER
		,RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER
		,LAST_PROCESSED_EVENT_HASH
		,LAST_PROCESSED_RECORD_HASH
	}
	
	private ConcurrentHashMap<ApplicationStatusCode, String> applicationStatusMap = new ConcurrentHashMap<ApplicationStatusCode, String>();
	
	private static final String updateSQL = "UPDATE t_application_status SET "
			+ " status_value = ? "
			+ " WHERE status_code = ?";
	
	private static final String selectSQL = 
			"SELECT status_value FROM t_application_status "
			+ " WHERE status_code = ?";
	
	private void updateStatus(ApplicationStatusCode code, String statusValue) throws Exception {
		
	    try (Connection connect = DatabaseUtilities.getConnection()) {
	    	log.trace("Updating application status for : {}", code.name());
	
			PreparedStatement updateValue = connect.prepareStatement(updateSQL);
	
			updateValue.setString(1, statusValue);
			updateValue.setString(2, code.name());
			
			updateValue.execute();
			updateValue.close();
			
			if (applicationStatusMap.containsKey(code)) {
				applicationStatusMap.replace(code, statusValue);
			} else {
				applicationStatusMap.put(code, statusValue);
			}
			
	    } catch (Exception e) {
			log.error("Error updating application status for : {}, {}", code.name(), e);
			throw e;
	    }
	}

	public String getStatus(ApplicationStatusCode code) throws Exception {
		String value = "";
		
		if (applicationStatusMap.containsKey(code)) {
			value = applicationStatusMap.get(code);
		} else {
		    try (Connection connect = DatabaseUtilities.getConnection()) {
		    	log.trace("Getting application status for : {}", code.name());
		
				PreparedStatement getValue = connect.prepareStatement(selectSQL);
		
				getValue.setString(1, code.name());
				getValue.execute();
				ResultSet appStatus = getValue.getResultSet();
	
				if (appStatus.next()) {
					value = appStatus.getString(1);
					if (value == null) { value = "";}
					applicationStatusMap.put(code, value);
				} else {
					log.error("Application status code {} does not exist in the database", code.name());
					throw new RuntimeException("Application status code " + code.name() + " does not exist in the database.");
				}
				appStatus.close();
				getValue.close();
		    } catch (Exception e) {
				log.error("Error getting application status for : {}, {}", code.name(), e);
				throw e;
		    }
		}
	    return value;
	}
	
	public String getLastValidDownloadedBalanceFileName() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE);
	}

	public void updateLastValidDownloadedBalanceFileName(String name) throws Exception {
		updateStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE, name);
	}
	
	public String getLastValidDownloadedRecordFileName() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
	}

	public void updateLastValidDownloadedRecordFileName(String name) throws Exception {
		updateStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, name);
	}

	public String getLastValidDownloadedRecordFileHash() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH);
	}

	public void updateLastValidDownloadedRecordFileHash(String hash) throws Exception {
		updateStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH, hash);
	}

	public String getLastValidDownloadedEventFileName() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE);
	}

	public void updateLastValidDownloadedEventFileName(String name) throws Exception {
		updateStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE, name);
	}

	public String getLastValidDownloadedEventFileHash() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH);
	}

	public void updateLastValidDownloadedEventFileHash(String hash) throws Exception {
		updateStatus(ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH, hash);
	}

	public String getBypassRecordHashMismatchUntilAfter() throws Exception {
		return getStatus(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
	}
	public void updateBypassRecordHashMismatchUntilAfter(String bypassUntilAfter) throws Exception {
		updateStatus(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER, bypassUntilAfter);
	}
	public String getBypassEventHashMismatchUntilAfter() throws Exception {
		return getStatus(ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
	}
	public void updateBypassEventHashMismatchUntilAfter(String bypassUntilAfter) throws Exception {
		updateStatus(ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER, bypassUntilAfter);
	}
	public String getLastProcessedRcdHash() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH);
	}

	public void updateLastProcessedRcdHash(String hash) throws Exception {
		if (hash.isEmpty()) {
			return;
		}
		if (!hash.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")) {
			updateStatus(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, hash);
		}
	}

	public String getLastProcessedEventHash() throws Exception {
		return getStatus(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH);
	}

	public void updateLastProcessedEventHash(String hash) throws Exception {
		if (hash.isEmpty()) {
			return;
		}
		if (!hash.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")) {
			updateStatus(ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH, hash);
		}
	}
}
