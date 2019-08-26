package com.hedera.recordFileLogger;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.databaseUtilities.ApplicationStatus.ApplicationStatusCode;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

@Log4j2
public class LoggerStatus {

	// Hash of last last successfully processed rcd file
	private static String lastProcessedRcdHash = "";

	// Hash of last last successfully processed eventStream file
	private static String lastProcessedEventHash = "";

	private static String configSavePath = "./config/loggerStatus.json";

	private static JsonObject jsonObject = new JsonObject();

	public LoggerStatus() {
		log.info("Loading configuration from {}", configSavePath);
		try {
			File file = new File (configSavePath);
			
			if (file.exists()) {
				jsonObject = getJsonObject(configSavePath);
				
				if (jsonObject.has("lastProcessedRcdHash")) {
					lastProcessedRcdHash = jsonObject.get("lastProcessedRcdHash").getAsString();
				}
				if (jsonObject.has("lastProcessedEventHash")) {
					lastProcessedEventHash = jsonObject.get("lastProcessedEventHash").getAsString();
				}
				if (ApplicationStatus.setStatus(ApplicationStatusCode.LPRH, lastProcessedRcdHash)) {
					if (ApplicationStatus.setStatus(ApplicationStatusCode.LPEH, lastProcessedEventHash)) {
						file.delete();
					}
				}
			} else {
				lastProcessedRcdHash = ApplicationStatus.getStatus(ApplicationStatusCode.LPRH);
				lastProcessedEventHash = ApplicationStatus.getStatus(ApplicationStatusCode.LPEH);
			}
		} catch (FileNotFoundException ex) {
			log.warn("Cannot load configuration from {}", configSavePath, ex);
		}
	}

	public static String getLastProcessedRcdHash() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LPRH);
	}

	public static void setLastProcessedRcdHash(String hash) {
		if (hash.isEmpty()) {
			return;
		}
		if (!hash.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")) {
			if (ApplicationStatus.setStatus(ApplicationStatusCode.LPRH, hash)) {
				log.debug("Update last processed record hash to be {}", hash);
			} else {
				log.error("Unable to save last processed record hash to database");
			}
		}
	}

	public static String getLastProcessedEventHash() {
		return ApplicationStatus.getStatus(ApplicationStatusCode.LPEH);
	}

	public static void setLastProcessedEventHash(String hash) {
		if (hash.isEmpty()) {
			return;
		}
		if (!hash.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")) {
			if (ApplicationStatus.setStatus(ApplicationStatusCode.LPEH, hash)) {
				log.debug("Update last processed event hash to be {}", hash);
			} else {
				log.error("Unable to save last processed event hash to database");
			}
		}
		
	}

	/***
	 *
	 * Reads a file into a Json object.
	 *
	 * @param location of the file
	 * @return a Json object with the contents of the file
	 * @throws JsonIOException if there are problems reading the file
	 * @throws JsonSyntaxException if the file does not represent a Json object
	 * @throws FileNotFoundException if the file doesn't exist
	 */
	private JsonObject getJsonObject(
			final String location) throws JsonIOException, JsonSyntaxException, FileNotFoundException {

		final JsonParser parser = new JsonParser();

		// Read file into object
		try {
			final FileReader file = new FileReader(location);
		} catch (FileNotFoundException e) {
			setLastProcessedRcdHash("");
			setLastProcessedEventHash("");
		}
		final FileReader file = new FileReader(location);
		return (JsonObject) parser.parse(file);
	}
}
