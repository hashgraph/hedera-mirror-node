package com.hedera.mirror.parser.balance;

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

import com.hedera.mirror.parser.FileWatcher;
import com.hedera.utilities.Utility;

import javax.inject.Named;

@Named
public class BalanceFileParser extends FileWatcher {

	public BalanceFileParser(BalanceParserProperties parserProperties) {
		super(parserProperties);
	}

	@Override
	public void onCreate() {
		processLastBalanceFile();
		processAllFilesForHistory();
	}

	protected boolean isEnabled() {
		return parserProperties.isEnabled();
	}

	private File getLatestBalanceFile() throws IOException {
		File lastFile = null;
        // find all files in path
        // return the greatest file name

        File balanceFilePath = parserProperties.getValidPath().toFile();
        List<String> balancefiles = new ArrayList<>();
	    for (final File balanceFile : balanceFilePath.listFiles()) {
	        if (balanceFile.getName().toString().endsWith(".csv") ) {
	            balancefiles.add(balanceFile.getName());
	        }

	    }
        if (balancefiles.size() != 0) {
            Collections.sort(balancefiles);

            lastFile = parserProperties.getValidPath().resolve(balancefiles.get(balancefiles.size() - 1)).toFile();
        }

        return lastFile;
	}

	private void processAllFilesForHistory() {
		Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            File balanceFilePath = parserProperties.getValidPath().toFile();
			File[] balanceFiles = balanceFilePath.listFiles();

	        for (final File balanceFile : balanceFiles) {
				if (Utility.checkStopFile()) {
					throw new RuntimeException("Stop file found, exiting");
				}
				if (new AccountBalancesFileLoader((BalanceParserProperties) parserProperties, balanceFile.toPath()).loadAccountBalances()) {
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

			if (new AccountBalancesFileLoader((BalanceParserProperties) parserProperties, balanceFile.toPath()).loadAccountBalances()) {
				// move it
				Utility.moveFileToParsedDir(balanceFile.getCanonicalPath(), "/parsedBalanceFiles/");
			}
		} catch (Exception e) {
			log.error("Error processing balances files after {}", stopwatch, e);
		}
	}
}
