package com.hedera.downloader;

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

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;
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

			Uninterruptibles.sleepUninterruptibly(15, TimeUnit.MILLISECONDS);
			
		}
	}
}
