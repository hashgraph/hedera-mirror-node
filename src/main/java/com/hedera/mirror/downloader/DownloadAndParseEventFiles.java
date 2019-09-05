package com.hedera.mirror.downloader;

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

import com.hedera.mirror.config.ConfigLoader;
import com.hedera.mirror.config.ConfigLoader.OPERATION_TYPE;
import com.hedera.mirror.parser.EventStreamFileParser;
import com.hedera.mirror.util.Utility;

@Log4j2
public class DownloadAndParseEventFiles {

	public static void main(String[] args) throws Exception {
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
