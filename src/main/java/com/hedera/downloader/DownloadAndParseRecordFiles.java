package com.hedera.downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.recordFileParser.RecordFileParser;
import com.hedera.utilities.Utility;

public class DownloadAndParseRecordFiles {
	protected static final Logger log = LogManager.getLogger("downloadandparserecordfiles");
	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	protected static ConfigLoader configLoader;
	
	public static void main(String[] args) {
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}
		configLoader = new ConfigLoader();

		RecordFileDownloader downloader = new RecordFileDownloader(configLoader);

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			// download files
			System.out.println("Downloading");
			RecordFileDownloader.downloadNewRecordfiles(downloader);
			System.out.println("Downloading Done");

			// process files
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			
			String pathName = configLoader.getDefaultParseDir();
			log.info(MARKER, "Record files folder got from configuration file: {}", configLoader.getDefaultParseDir());
	
			if (pathName != null) {
				System.out.println("Parsing Done");
				RecordFileParser.parseNewFiles(pathName);
				System.out.println("Parsing Done");
			}
		}
	}

}
