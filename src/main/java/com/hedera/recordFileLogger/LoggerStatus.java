package com.hedera.recordFileLogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class LoggerStatus {

	private static final Logger log = LogManager.getLogger("recordStream-log");
	private static final Marker MARKER = MarkerManager.getMarker("LoggerStatus");

	// Hash of last last successfully processed rcd file
	private static String lastProcessedRcdHash = "";

	private static String configSavePath = "./config/loggerStatus.json";

	private static JsonObject jsonObject = new JsonObject();

	public LoggerStatus(String configPath) {
		configSavePath = configPath;
		log.info(MARKER, "Loading configuration from {}", configPath);
		try {
			jsonObject = getJsonObject(configPath);
			
			if (jsonObject.has("lastProcessedRcdHash")) {
				lastProcessedRcdHash = jsonObject.get("lastProcessedRcdHash").getAsString();
			}
		} catch (FileNotFoundException ex) {
			log.warn(MARKER, "Cannot load configuration from {}, Exception: {}", configPath, ex);
		}
	}

	public String getLastProcessedRcdHash() {
		return lastProcessedRcdHash;
	}

	public void setLastProcessedRcdHash(String name) {
		lastProcessedRcdHash = name;
		if (name.isEmpty()) {
			return;
		}
		if (!name.contentEquals("000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")) {
			jsonObject.addProperty("lastProcessedRcdHash", name);
			log.info(MARKER, "Update lastProcessedRcdHash to be {}", name);
		}
	}

	public void saveToFile() {
		try (FileWriter file = new FileWriter(configSavePath)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(jsonObject, file);
			log.info(MARKER, "Successfully wrote configuration to {}", configSavePath);
		} catch (IOException ex) {
			log.warn(MARKER, "Fail to write configuration to {}, Exception: {}", configSavePath, ex.getStackTrace());
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
	public JsonObject getJsonObject(
			final String location) throws JsonIOException, JsonSyntaxException, FileNotFoundException {

		final JsonParser parser = new JsonParser();

		// Read file into object
		try {
			final FileReader file = new FileReader(location);
		} catch (FileNotFoundException e) {
			setLastProcessedRcdHash("");
			saveToFile();
		}
		final FileReader file = new FileReader(location);
		return (JsonObject) parser.parse(file);
	}
}
