package com.hedera.downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.EventStreamFileParser;
import com.hedera.utilities.Utility;

public class DownloadAndParseEventFiles {
	protected static final Logger log = LogManager.getLogger("downloadandparseeventfiles");
	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	public static void main(String[] args) {
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}
		EventStreamFileDownloader downloader = new EventStreamFileDownloader();

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			// download files
			EventStreamFileDownloader.downloadNewEventfiles(downloader);

			// process files
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}

			String pathName = configLoader.getDefaultParseDir(OPERATION_TYPE.EVENTS);
			log.info(MARKER, "Event files folder got from configuration file: {}", pathName);

			if (pathName != null) {
				EventStreamFileParser.parseNewFiles(pathName);
			}
		}
	}

}
