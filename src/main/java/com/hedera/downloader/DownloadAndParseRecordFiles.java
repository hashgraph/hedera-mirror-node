package com.hedera.downloader;

import lombok.extern.log4j.Log4j2;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.parser.RecordFileParser;
import com.hedera.utilities.Utility;

@Log4j2
public class DownloadAndParseRecordFiles {

	public static void main(String[] args) throws Exception {
		RecordFileDownloader downloader = new RecordFileDownloader();

		while (true) {
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			// download files
 			RecordFileDownloader.downloadNewRecordfiles(downloader);

			// process files
			if (Utility.checkStopFile()) {
				log.info("Stop file found, stopping");
				break;
			}
			
			String pathName = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
	
			if (pathName != null) {
				RecordFileParser.parseNewFiles(pathName);
			}
		}
	}
}
