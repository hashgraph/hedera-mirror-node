package com.hedera.downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.RecordFileParser;
import com.hedera.utilities.Utility;

public class DownloadAndParseRecordFiles {
	protected static final Logger log = LogManager.getLogger("downloadandparserecordfiles");
	protected static final Marker MARKER = MarkerManager.getMarker("DOWNLOADER");

	public static void main(String[] args) {
		if (Utility.checkStopFile()) {
			log.info(MARKER, "Stop file found, exiting.");
			System.exit(0);
		}
		RecordFileDownloader downloader = new RecordFileDownloader();

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			// download files
 			RecordFileDownloader.downloadNewRecordfiles(downloader);

			// process files
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				break;
			}
			
			String pathName = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
			log.info(MARKER, "Record files folder got from configuration file: {}", pathName);
	
			if (pathName != null) {
				RecordFileParser.parseNewFiles(pathName);
			}
		}
	}

}
