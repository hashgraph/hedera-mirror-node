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

import com.amazonaws.services.s3.transfer.TransferManager;

import com.hedera.FileCopier;
import com.hedera.mirror.addressbook.NetworkAddressBook;
import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.config.MirrorNodeConfiguration;
import com.hedera.mirror.domain.HederaNetwork;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.downloader.CommonDownloaderProperties;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.utilities.Utility;
import io.findify.s3mock.S3Mock;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountBalancesDownloaderTest {

    @Mock
    private ApplicationStatusRepository applicationStatusRepository;

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    private S3Mock s3;
    private FileCopier fileCopier;
    private AccountBalancesDownloader downloader;
    private CommonDownloaderProperties commonDownloaderProperties;
    private MirrorProperties mirrorProperties;
    private NetworkAddressBook networkAddressBook;
    private BalanceDownloaderProperties downloaderProperties;

    @BeforeEach
    void before() throws Exception {
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setNetwork(HederaNetwork.TESTNET);
        commonDownloaderProperties = new CommonDownloaderProperties();
        commonDownloaderProperties.setBucketName("test");
        commonDownloaderProperties.setCloudProvider(CommonDownloaderProperties.CloudProvider.LOCAL);
        downloaderProperties = new BalanceDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        downloaderProperties.init();
        networkAddressBook = new NetworkAddressBook(mirrorProperties);
        TransferManager transferManager = new MirrorNodeConfiguration().transferManager(commonDownloaderProperties);

        downloader = new AccountBalancesDownloader(transferManager, applicationStatusRepository, networkAddressBook, downloaderProperties);

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath())
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());

        s3 = S3Mock.create(8001, s3Path.toString());
        s3.start();

        when(applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_BALANCE_FILE)).thenReturn("");
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
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isBalanceFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get("2019-08-30T18_15_00.016002001Z_Balances.csv"))
                .contains(Paths.get("2019-08-30T18_30_00.010147001Z_Balances.csv"));
    }

    @Test
    @DisplayName("Missing address book")
    void missingAddressBook() throws Exception {
        Files.delete(mirrorProperties.getAddressBookPath());
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        downloaderProperties.setBatchSize(1);
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(1)
                .allMatch(p -> Utility.isBalanceFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get("2019-08-30T18_15_00.016002001Z_Balances.csv"));
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles("*Balances.csv").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Missing balances")
    void missingBalances() throws Exception {
        fileCopier.filterFiles("*_sig").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Less than 2/3 signatures")
    void lessThanTwoThirdSignatures() throws Exception {
        fileCopier.filterDirectories("balance0.0.3").filterDirectories("balance0.0.4").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isBalanceSigFile(p.toString())).forEach(AccountBalancesDownloaderTest::corruptFile);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Invalid or incomplete file")
    void invalidBalanceFile() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isBalanceFile(p.toString())).forEach(AccountBalancesDownloaderTest::corruptFile);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Error moving file to valid folder")
    void errorMovingFile() throws Exception {
        fileCopier.copy();
        downloaderProperties.getValidPath().toFile().delete();
        downloader.download();
        assertThat(downloaderProperties.getValidPath()).doesNotExist();
    }

    private static void corruptFile(Path p) {
        try {
            File file = p.toFile();
            if (file.isFile()) {
                FileUtils.writeStringToFile(file, "corrupt", "UTF-8", true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
