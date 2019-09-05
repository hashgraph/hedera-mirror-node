package db.migration;

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

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.hedera.mirror.util.ApplicationStatus;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;

@Log4j2
public class V1_8__MigrateConfig extends BaseJavaMigration {
    public void migrate(Context context) throws Exception {
    	
    	String configSavePath = "./config/config.json";
    	String balanceSavePath = "./config/balance.json";
    	String recordsSavePath = "./config/records.json";
    	String eventsSavePath = "./config/events.json";
    	String loggerSavePath = "./config/loggerStatus.json";
    	
    	JsonObject configJsonObject;
    	JsonObject balanceJsonObject;
    	JsonObject recordsJsonObject;
    	JsonObject eventsJsonObject;
    	JsonObject loggerJsonObject;
    	
    	String stopLoggingIfRecordHashMismatchAfter = "";
    	String stopLoggingIfEventHashMismatchAfter = "";
    	String lastValidBalanceFileName = "";
    	String lastValidRcdFileName = "";
    	String lastValidRcdFileHash = "";
    	String lastValidEventFileName = "";
    	String lastValidEventFileHash = "";
    	String lastProcessedRcdHash = "";
    	String lastProcessedEventHash = "";
    	
		configJsonObject = getJsonObject(configSavePath);

		if (configJsonObject.has("stopLoggingIfRecordHashMismatch")) {
			stopLoggingIfRecordHashMismatchAfter = configJsonObject.get("stopLoggingIfRecordHashMismatch").getAsString();
		}
		
		if (configJsonObject.has("stopLoggingIfEventHashMismatch")) {
			stopLoggingIfEventHashMismatchAfter = configJsonObject.get("stopLoggingIfEventHashMismatch").getAsString();
		}
		File balanceFile = new File(balanceSavePath);
		if (balanceFile.exists()) {
			balanceJsonObject = getJsonObject(balanceSavePath);
			if (balanceJsonObject.has("lastValidBalanceFileName")) {
				lastValidBalanceFileName = balanceJsonObject.get("lastValidBalanceFileName").getAsString();
			}
		} else if (configJsonObject.has("lastValidBalanceFileName")) {
			lastValidBalanceFileName = configJsonObject.get("lastValidBalanceFileName").getAsString();
		}

		File recordFile = new File(recordsSavePath);
		if (recordFile.exists()) {
			recordsJsonObject = getJsonObject(recordsSavePath);
			if (recordsJsonObject.has("lastValidRcdFileName")) {
				lastValidRcdFileName = recordsJsonObject.get("lastValidRcdFileName").getAsString();
			}
			if (recordsJsonObject.has("lastValidRcdFileHash")) {
				lastValidRcdFileHash = recordsJsonObject.get("lastValidRcdFileHash").getAsString();
			}
		} else if ((configJsonObject.has("lastValidRcdFileName")) || (configJsonObject.has("lastValidRcdFileHash"))) {
			if (configJsonObject.has("lastValidRcdFileName")) {
				lastValidRcdFileName = configJsonObject.get("lastValidRcdFileName").getAsString();
			}
			if (configJsonObject.has("lastValidRcdFileHash")) {
				lastValidRcdFileHash = configJsonObject.get("lastValidRcdFileHash").getAsString();
			}
		}

		File eventFile = new File(eventsSavePath);
		if (eventFile.exists()) {
			eventsJsonObject = getJsonObject(eventsSavePath);
			if (eventsJsonObject.has("lastValidEventFileName")) {
				lastValidEventFileName = eventsJsonObject.get("lastValidEventFileName").getAsString();
			}
			if (eventsJsonObject.has("lastValidEventFileHash")) {
			    lastValidEventFileHash = eventsJsonObject.get("lastValidEventFileHash").getAsString();
			}
		}
		
		File loggerFile = new File (loggerSavePath);
		
		if (loggerFile.exists()) {
			loggerJsonObject = getJsonObject(loggerSavePath);
			
			if (loggerJsonObject.has("lastProcessedRcdHash")) {
				lastProcessedRcdHash = loggerJsonObject.get("lastProcessedRcdHash").getAsString();
			}
			if (loggerJsonObject.has("lastProcessedEventHash")) {
				lastProcessedEventHash = loggerJsonObject.get("lastProcessedEventHash").getAsString();
			}
		}

		try (PreparedStatement updateValue = context.getConnection().prepareStatement(
				"UPDATE t_application_status SET "
				+ " status_value = ? "
				+ " WHERE status_code = ?")) {
			updateValue.setString(1, stopLoggingIfEventHashMismatchAfter);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER.name());
			updateValue.execute();
			updateValue.setString(1, stopLoggingIfRecordHashMismatchAfter);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER.name());
			updateValue.execute();
			updateValue.setString(1, lastValidBalanceFileName);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE.name());
			updateValue.execute();
			updateValue.setString(1, lastValidRcdFileName);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE.name());
			updateValue.execute();
			updateValue.setString(1, lastValidRcdFileHash);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH.name());
			updateValue.execute();
			updateValue.setString(1, lastValidEventFileName);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE.name());
			updateValue.execute();
			updateValue.setString(1, lastValidEventFileHash);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH.name());
			updateValue.execute();
			updateValue.setString(1, lastProcessedRcdHash);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH.name());
			updateValue.execute();
			updateValue.setString(1, lastProcessedEventHash);
			updateValue.setString(2, ApplicationStatus.ApplicationStatusCode.LAST_PROCESSED_EVENT_HASH.name());
			updateValue.execute();
		}
		
		// remove from config file
		configJsonObject.remove("lastValidRcdFileName");
		configJsonObject.remove("lastValidRcdFileHash");
		configJsonObject.remove("stopLoggingIfRecordHashMismatch");
		configJsonObject.remove("stopLoggingIfEventHashMismatch");
		configJsonObject.remove("lastValidBalanceFileName");

		try (FileWriter configFile = new FileWriter(configSavePath)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(configJsonObject, configFile);
			log.debug("Successfully wrote update to {}", configSavePath);
		} catch (IOException ex) {
			log.error("Fail to write update to {}", configSavePath, ex);
			throw ex;
		}

		if (balanceFile.exists()) {
			balanceFile.delete();
		}
		if (recordFile.exists()) {
			recordFile.delete();
		}
		if (eventFile.exists()) {
			eventFile.delete();
		}
		if (loggerFile.exists()) {
			loggerFile.delete();
		}
    }
    
	private JsonObject getJsonObject(
			final String location) throws JsonIOException, JsonSyntaxException, FileNotFoundException {

		final JsonParser parser = new JsonParser();

		// Read file into object
		final FileReader file = new FileReader(location);
		return (JsonObject) parser.parse(file);
	}    
	
}
