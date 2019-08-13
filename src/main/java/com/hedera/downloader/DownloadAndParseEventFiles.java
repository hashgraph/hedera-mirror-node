package com.hedera.downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.EventStreamFileParser;
import com.hedera.parser.RecordFileParser;
import com.hedera.utilities.Utility;

public class DownloadAndParseEventFiles {
	protected static final Logger log = LogManager.getLogger("downloadandparseeventfiles");
	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	protected static ConfigLoader configLoader;
	
	public static void main(String[] args) {
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}
		configLoader = new ConfigLoader();

		EventStreamFileDownloader downloader = new EventStreamFileDownloader(configLoader);

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			// download files
			System.out.println("Downloading");
			EventStreamFileDownloader.downloadNewEventfiles(downloader);
			System.out.println("Downloading Done");

			// process files
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			
			String pathName = configLoader.getDefaultParseDir(OPERATION_TYPE.EVENTS);
			log.info(MARKER, "Event files folder got from configuration file: {}", pathName);
	
			if (pathName != null) {
				EventStreamFileParser.parseNewFiles(pathName);
				System.out.println("Parsing Done");
			}
		}
	}

}
