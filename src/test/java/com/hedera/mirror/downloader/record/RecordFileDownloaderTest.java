package com.hedera.mirror.downloader.record;

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TestName;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecordFileDownloaderTest {

    @Mock
    private ApplicationStatusRepository applicationStatusRepository;

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    @Rule
    public TestName name = new TestName();

    private S3Mock s3;
    private FileCopier fileCopier;
    private RecordFileDownloader downloader;
    private CommonDownloaderProperties commonDownloaderProperties;
    private MirrorProperties mirrorProperties;
    private NetworkAddressBook networkAddressBook;
    private RecordDownloaderProperties downloaderProperties;

    @BeforeEach
    void before(TestInfo testInfo) {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());
        mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        mirrorProperties.setNetwork(HederaNetwork.TESTNET);
        commonDownloaderProperties = new CommonDownloaderProperties();
        commonDownloaderProperties.setBucketName("test");
        commonDownloaderProperties.setCloudProvider(CommonDownloaderProperties.CloudProvider.LOCAL);
        downloaderProperties = new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        downloaderProperties.init();
        networkAddressBook = new NetworkAddressBook(mirrorProperties);
        TransferManager transferManager = new MirrorNodeConfiguration().transferManager(commonDownloaderProperties);

        downloader = new RecordFileDownloader(transferManager, applicationStatusRepository, networkAddressBook, downloaderProperties);

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath(), "v2")
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());

        s3 = S3Mock.create(8001, s3Path.toString());
        s3.start();
    }

    @AfterEach
    void after(TestInfo testInfo) {
        s3.shutdown();
        System.out.println("After test: " + testInfo.getTestMethod().get().getName());
        System.out.println("##########################################\n");
    }

    @Test
    @DisplayName("Download and verify V1 files")
    void downloadV1() throws Exception {
        Path addressBook = ResourceUtils.getFile("classpath:addressbook/test-v1").toPath();
        mirrorProperties.setAddressBookPath(addressBook);
        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath(), "v1")
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());
        fileCopier.copy();
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);

        downloader.download();

        verify(applicationStatusRepository).updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-07-01T14:13:00.317763Z.rcd");
        verify(applicationStatusRepository).updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-07-01T14:29:00.302068Z.rcd");
        verify(applicationStatusRepository, times(2)).updateStatusValue(eq(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH), any());
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isRecordFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get("2019-07-01T14:13:00.317763Z.rcd"))
                .contains(Paths.get("2019-07-01T14:29:00.302068Z.rcd"));
    }

    @Test
    @DisplayName("Download and verify V2 files")
    void downloadV2() throws Exception {
        fileCopier.copy();
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
        downloader.download();
        verify(applicationStatusRepository).updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-08-30T18_10_00.419072Z.rcd");
        verify(applicationStatusRepository).updateStatusValue(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-08-30T18_10_05.249678Z.rcd");
        verify(applicationStatusRepository, times(2)).updateStatusValue(eq(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH), any());
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isRecordFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get("2019-08-30T18_10_05.249678Z.rcd"))
                .contains(Paths.get("2019-08-30T18_10_00.419072Z.rcd"));
    }

    @Test
    @DisplayName("Missing address book")
    void missingAddressBook() throws Exception {
        Files.delete(mirrorProperties.getAddressBookPath());
        fileCopier.copy();
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
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
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(1)
                .allMatch(p -> Utility.isRecordFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get("2019-08-30T18_10_00.419072Z.rcd"));
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles("*.rcd").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Missing records")
    void missingRecords() throws Exception {
        fileCopier.filterFiles("*_sig").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Less than 2/3 signatures")
    void lessThanTwoThirdSignatures() throws Exception {
        fileCopier.filterDirectories("record0.0.3").filterDirectories("record0.0.4").copy();
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isRecordSigFile(p.toString())).forEach(RecordFileDownloaderTest::corruptFile);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() throws Exception {
        final String filename = "2019-08-30T18_10_05.249678Z.rcd";
        doReturn("2019-07-01T14:12:00.000000Z.rcd").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
        doReturn("123").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH);
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
        fileCopier.filterFiles(filename + "*").copy(); // Skip first file with zero hash
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Bypass previous hash mismatch")
    void hashMismatchWithBypass() throws Exception {
        final String filename = "2019-08-30T18_10_05.249678Z.rcd";
        doReturn("2019-07-01T14:12:00.000000Z.rcd").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
        doReturn("123").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH);
        doReturn("2019-09-01T00:00:00.000000Z.rcd").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER);
        fileCopier.filterFiles(filename + "*").copy(); // Skip first file with zero hash
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(1)
                .allMatch(p -> Utility.isRecordFile(p.toString()))
                .extracting(Path::getFileName)
                .contains(Paths.get(filename));
    }

    @Test
    @DisplayName("Invalid or incomplete record file")
    void invalidRecord() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isRecordFile(p.toString())).forEach(RecordFileDownloaderTest::corruptFile);
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
        downloader.download();
        assertThat(Files.walk(downloaderProperties.getValidPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Error moving record to valid folder")
    void errorMovingFile() throws Exception {
        fileCopier.copy();
        doReturn("").when(applicationStatusRepository).findByStatusCode(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE);
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
