package com.hedera.mirror.downloader.balance;

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

import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.downloader.Downloader;
import com.hedera.mirror.downloader.DownloaderProperties;
import com.hedera.mirror.downloader.DownloaderTestingBase;
import com.hedera.utilities.Utility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountBalancesDownloaderTest extends DownloaderTestingBase {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        DownloaderProperties properties = new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        properties.init();
        return properties;
    }

    @Override
    protected Downloader getDownloader() {
        return new AccountBalancesDownloader(s3AsyncClient, applicationStatusRepository, networkAddressBook,
                (BalanceDownloaderProperties)downloaderProperties);
    }

    @Override
    protected boolean isSigFile(String file) {
        return Utility.isBalanceSigFile(file);
    }

    @Override
    protected boolean isDataFile(String file) {
        return Utility.isBalanceFile(file);
    }

    @BeforeEach
    void before(TestInfo testInfo) {
        super.beforeEach(testInfo, "accountBalances");
    }

    @AfterEach
    void after() {
        s3.shutdown();
    }

    @Test
    @DisplayName("Download and verify signatures")
    void downloadAndVerify() throws Exception {
        fileCopier.copy();
        downloader.download();
        verify(applicationStatusRepository).updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE, "2019-08-30T18_30_00.010147001Z_Balances.csv");
        assertValidFiles(List.of("2019-08-30T18_15_00.016002001Z_Balances.csv", "2019-08-30T18_30_00.010147001Z_Balances.csv"));
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        ((BalanceDownloaderProperties)downloaderProperties).setBatchSize(1);
        testMaxDownloadItemsReached("2019-08-30T18_15_00.016002001Z_Balances.csv");
    }
}
