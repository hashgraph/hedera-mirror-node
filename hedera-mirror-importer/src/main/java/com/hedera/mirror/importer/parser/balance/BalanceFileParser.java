package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;

import com.google.common.base.Stopwatch;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import javax.inject.Named;
import org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.parser.FileWatcher;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

@Named
public class BalanceFileParser extends FileWatcher {

    private final AccountBalancesFileLoader accountBalancesFileLoader;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    public BalanceFileParser(BalanceParserProperties parserProperties,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
            AccountBalancesFileLoader accountBalancesFileLoader) {
        super(parserProperties);
        this.accountBalancesFileLoader = accountBalancesFileLoader;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
    }

    /**
     * List the verified balance files and parse them. We can process them in any order, but we choose to process the
     * latest balance first since most clients will want to query for the latest data.
     */
    @Override
    public void parse() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        DateRangeFilter dateRangeFilter = mirrorDateRangePropertiesProcessor.getDateRangeFilter(parserProperties.getStreamType());

        try {
            File balanceFilePath = parserProperties.getValidPath().toFile();
            File[] balanceFiles = Objects.requireNonNullElseGet(balanceFilePath.listFiles(), () -> new File[] {});
            Arrays.sort(balanceFiles, Collections.reverseOrder());

            for (File balanceFile : balanceFiles) {
                if (ShutdownHelper.isStopping()) {
                    throw new RuntimeException("Process is shutting down");
                }
                parseBalanceFile(balanceFile, dateRangeFilter);
            }

            log.info("Completed processing {} balance files in {}", balanceFiles.length, stopwatch);
        } catch (Exception e) {
            log.error("Error processing balances files after {}", stopwatch, e);
        }
    }

    private void parseBalanceFile(File balanceFile, DateRangeFilter dateRangeFilter) {
        try {
            accountBalancesFileLoader.loadAccountBalances(balanceFile, dateRangeFilter);
            if (parserProperties.isKeepFiles()) {
                Utility.archiveFile(balanceFile, parserProperties.getParsedPath());
            } else {
                FileUtils.deleteQuietly(balanceFile);
            }
        } catch (Exception ex) {
            log.error("Failed to load account balance file " + balanceFile.getName(), ex);
        }
    }
}
