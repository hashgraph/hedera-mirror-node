package com.hedera.mirror.importer.downloader.balance;

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

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import com.hedera.mirror.importer.util.Utility;

class AccountBalancesDownloaderTest extends AbstractDownloaderTest {

    private final Map<String, AccountBalanceFile> accountBalanceFileMap = new HashMap<>();

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader getDownloader() {
        BalanceFileReader balanceFileReader = new BalanceFileReaderImplV1(new BalanceParserProperties(mirrorProperties),
                new AccountBalanceLineParserV1(mirrorProperties));
        return new AccountBalancesDownloader(s3AsyncClient, addressBookService,
                (BalanceDownloaderProperties) downloaderProperties, meterRegistry, nodeSignatureVerifier,
                signatureFileReader, balanceFileReader, streamFileNotifier, dateRangeProcessor);
    }

    @Override
    protected Path getTestDataDir() {
        return Path.of("accountBalances", "v1");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofMinutes(15L);
    }

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        setTestFilesAndInstants(List.of(
                "2019-08-30T18_15_00.016002001Z_Balances.csv",
                "2019-08-30T18_30_00.010147001Z_Balances.csv"
        ));

        long timestamp = Utility.convertToNanosMax(file1Instant.getEpochSecond(), file1Instant.getNano());
        AccountBalanceFile abf1 = AccountBalanceFile.builder()
                .consensusTimestamp(timestamp)
                .count(0L)
                .fileHash(
                        "c1a6ffb5df216a1e8331f949f45cb9400fc474150d57d977c77f21318687eb18d407c780147d0435791a02743a0f7bfc")
                .loadEnd(0L)
                .loadStart(0L)
                .name(file1)
                .nodeAccountId(null)
                .build();
        accountBalanceFileMap.put(file1, abf1);

        timestamp = Utility.convertToNanosMax(file2Instant.getEpochSecond(), file2Instant.getNano());
        AccountBalanceFile abf2 = AccountBalanceFile.builder()
                .consensusTimestamp(timestamp)
                .count(0L)
                .fileHash(
                        "c197898e485e92a85752d475b536e6dc09879a18d358b1e72a9a1160bb24c8bb7a4c58610383ac80fd1c7659214eccd4")
                .loadEnd(0L)
                .loadStart(0L)
                .name(file2)
                .nodeAccountId(null)
                .build();
        accountBalanceFileMap.put(file2, abf2);
    }
}
