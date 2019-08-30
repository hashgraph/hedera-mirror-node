package com.hedera.balancefilelogger;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Stopwatch;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.databaseutilities.DatabaseUtilities;
import com.hedera.filewatcher.FileWatcher;
import com.hedera.mirror.dataset.AccountBalancesFileLoader;
import com.hedera.utilities.Utility;

import java.time.Instant;

public class BalanceFileLogger extends FileWatcher {

	enum BalanceSelect {
		ZERO
		,SHARD
		,REALM
		,NUM
	}

	enum BalanceUpdate {
		ZERO
		,BALANCE
		,ID
	}

	enum BalanceInsert {
		ZERO
		,SHARD
		,REALM
		,NUM
		,BALANCE
	}

    enum BalanceHistoryInsert {
        ZERO
        ,SNAPSHOT_TIME
        ,SECONDS
        ,NANOS
        ,SNAPSHOT_TIME_NS
        ,FK_BAL_ID
        ,BALANCE
    }

    enum BalanceHistoryInsertBalance {
        ZERO
        ,SHARD
        ,REALM
        ,NUM
    }

	private static Instant fileTimestamp;
	private static long fileSeconds = 0;
	private static long fileNanos = 0;
	private static File balanceFilePath = new File(ConfigLoader.getDefaultParseDir(OPERATION_TYPE.BALANCE));
	
	public BalanceFileLogger(File pathToWatch) {
		super(pathToWatch);
	}

	public static void main(String[] args) {
		FileWatcher fileWatcher = new BalanceFileLogger(balanceFilePath);
		fileWatcher.watch();
	}

	@Override
	public void onCreate() {
		processLastBalanceFile();
		processAllFilesForHistory();
	}

	private File getLatestBalanceFile() throws IOException {

		File lastFile = null;
        // find all files in path
        // return the greatest file name

        List<String> balancefiles = new ArrayList<String>();
	    for (final File balanceFile : balanceFilePath.listFiles()) {
	        if (balanceFile.getName().toString().endsWith(".csv") ) {
	            balancefiles.add(balanceFile.getName());
	        }
	    	
	    }
        if (balancefiles.size() != 0) {
            Collections.sort(balancefiles);

            lastFile = new File(balanceFilePath + File.separator + balancefiles.get(balancefiles.size()-1));
        }

        return lastFile;
	}

	private void processAllFilesForHistory() {
		Stopwatch stopwatch = Stopwatch.createStarted();

        try {
        	File balanceFilesPath = balanceFilePath;
			File[] balanceFiles = balanceFilesPath.listFiles();

	        for (final File balanceFile : balanceFiles) {
				if (Utility.checkStopFile()) {
					throw new RuntimeException("Stop file found, exiting");
				}
				if (new AccountBalancesFileLoader(balanceFile.toPath()).loadAccountBalances()) {
					// move it
					Utility.moveFileToParsedDir(balanceFile.getCanonicalPath(), "/parsedBalanceFiles/"); 
				}
	        }
			log.info("Completed processing {} balance files in {}", balanceFiles.length, stopwatch);
		} catch (Exception e) {
            log.error("Error processing balances files after {}", stopwatch, e);
		}
	}

	private void processLastBalanceFile() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
			File balanceFile = getLatestBalanceFile();

			if (balanceFile == null) {
				return;
			}

			log.debug("Processing last balance file {}", balanceFile);

			if (new AccountBalancesFileLoader(balanceFile.toPath()).loadAccountBalances()) {
				// move it
				Utility.moveFileToParsedDir(balanceFile.getCanonicalPath(), "/parsedBalanceFiles/");
			}
		} catch (Exception e) {
			log.error("Error processing balances files after {}", stopwatch, e);
		}
	}
}
