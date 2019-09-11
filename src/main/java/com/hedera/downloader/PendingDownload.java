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

import com.amazonaws.services.s3.transfer.Download;
import com.google.common.base.Stopwatch;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;

import java.io.File;

/**
 * The results of a pending download from the AWS TransferManager.
 * Call waitForCompletion() to wait for the transfer to complete and get the status of whether it was successful
 * or not.
 */
@Log4j2
@Value
@ToString(includeFieldNames=true)
public class PendingDownload {
	Download download;
	Stopwatch stopwatch;
	File file; // Destination file
	String s3key; // Source S3 key
	@NonFinal boolean alreadyWaited = false; // has waitForCompletion been called
	@NonFinal boolean downloadSuccessful;

	public PendingDownload(final Download download, final File file, final String s3key) {
		this.download = download;
		this.stopwatch = Stopwatch.createStarted();
		this.file = file;
		this.s3key = s3key;
	}

	/**
	 * @return true if the download was successful.
	 * @throws InterruptedException
	 */
	public boolean waitForCompletion() throws InterruptedException {
		if (alreadyWaited) {
			return downloadSuccessful;
		}
		alreadyWaited = true;
		try {
			download.waitForCompletion();
			if (download.isDone()) {
				log.debug("Finished downloading {} in {}", s3key, stopwatch);
				downloadSuccessful = true;
			} else {
				log.error("Failed downloading {} after {}", s3key, stopwatch);
				downloadSuccessful = false;
			}
		} catch (Exception ex) {
			log.error("Failed downloading {} after {}", s3key, stopwatch, ex);
			downloadSuccessful = false;
		}
		return downloadSuccessful;
	}
}
