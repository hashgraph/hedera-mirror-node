package com.hedera.mirror.importer.downloader.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.downloader.AbstractDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;

@ExtendWith(MockitoExtension.class)
public class AccountBalancesDownloaderTest extends AbstractDownloaderTest {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
       return new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader getDownloader() {
        return new AccountBalancesDownloader(s3AsyncClient, applicationStatusRepository, networkAddressBook,
                (BalanceDownloaderProperties) downloaderProperties, meterRegistry);
    }

    @Override
    protected Path getTestDataDir() {
        return Path.of("accountBalances");
    }

    @BeforeEach
    void beforeEach() {
        file1 = "2019-08-30T18_15_00.016002001Z_Balances.csv";
        file2 = "2019-08-30T18_30_00.010147001Z_Balances.csv";
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        ((BalanceDownloaderProperties) downloaderProperties).setBatchSize(1);
        testMaxDownloadItemsReached(file1);
    }
}
