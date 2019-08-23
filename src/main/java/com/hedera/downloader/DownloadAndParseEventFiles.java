package com.hedera.downloader;

import lombok.extern.log4j.Log4j2;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.EventStreamFileParser;
import com.hedera.utilities.Utility;

@Log4j2
public class DownloadAndParseEventFiles {

	public static void main(String[] args) {
		EventStreamFileDownloader downloader = new EventStreamFileDownloader();

		while (true) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			// download files
			EventStreamFileDownloader.downloadNewEventfiles(downloader);

			// process files
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}

			String pathName = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.EVENTS);

			if (pathName != null) {
				EventStreamFileParser.parseNewFiles(pathName);
			}
		}
	}
}
