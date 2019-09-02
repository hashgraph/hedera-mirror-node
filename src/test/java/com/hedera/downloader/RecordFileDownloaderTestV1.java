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

import com.hedera.FileCopier;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.utilities.Utility;
import io.findify.s3mock.S3Mock;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecordFileDownloaderTestV1 {

    @Mock
    private ApplicationStatus applicationStatus;

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    private Path validPath;
    private S3Mock s3;
    private FileCopier fileCopier;
    private RecordFileDownloader downloader;

    @BeforeEach
    void before() throws Exception {
        ConfigLoader.setAddressBookFile("./config/0.0.102.sample");
        ConfigLoader.setCloudProvider(ConfigLoader.CLOUD_PROVIDER.LOCAL);
        ConfigLoader.setDownloadToDir(dataPath.toAbsolutePath().toString());
        ConfigLoader.setMaxDownloadItems(100);

        downloader = new RecordFileDownloader();
        downloader.applicationStatus = applicationStatus;

        validPath = Paths.get(ConfigLoader.getDefaultParseDir(ConfigLoader.OPERATION_TYPE.RECORDS));
        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from("recordstreamsV1")
                .to(ConfigLoader.getBucketName(), "recordstreams");

        s3 = S3Mock.create(8001, s3Path.toString());
        s3.start();

        when(applicationStatus.getLastValidDownloadedRecordFileName()).thenReturn("");
        when(applicationStatus.getLastValidDownloadedRecordFileHash()).thenReturn("");
    }

    @AfterEach
    void after() {
        s3.shutdown();
    }

    @Test
    @DisplayName("Downloaded and verified")
    void download() throws Exception {
        fileCopier.copy();
        downloader.download();
        verify(applicationStatus).updateLastValidDownloadedRecordFileName("2019-07-01T14:29:00.302068Z.rcd");
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isRecordFile(p.toString()));
    }

    @Test
    @DisplayName("Missing address book")
    void missingAddressBook() throws Exception {
        ConfigLoader.setAddressBookFile("");
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        ConfigLoader.setMaxDownloadItems(1);
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(1)
                .allMatch(p -> Utility.isRecordFile(p.toString()));
    }

    @Test
    @DisplayName("Missing signatures")
    void missingSignatures() throws Exception {
        fileCopier.filterFiles("*.rcd").copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Missing records")
    void missingRecords() throws Exception {
        fileCopier.filterFiles("*_sig").copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Less than 2/3 signatures")
    void lessThanTwoThirdSignatures() throws Exception {
        fileCopier.filterDirectories("record0.0.3").filterDirectories("record0.0.4").copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Signature doesn't match file")
    void signatureMismatch() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isRecordSigFile(p.toString())).forEach(RecordFileDownloaderTestV1::corruptFile);
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() throws Exception {
        when(applicationStatus.getLastValidDownloadedRecordFileName()).thenReturn("2019-07-01T14:12:00.000000Z.rcd");
        when(applicationStatus.getLastValidDownloadedRecordFileHash()).thenReturn("123");
        when(applicationStatus.getBypassRecordHashMismatchUntilAfter()).thenReturn("");
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isRecordFile(p.toString()));
    }

    @Test
    @DisplayName("Bypass previous hash mismatch")
    void hashMismatchWithBypass() throws Exception {
        when(applicationStatus.getLastValidDownloadedRecordFileName()).thenReturn("2019-07-01T14:12:00.000000Z.rcd");
        when(applicationStatus.getLastValidDownloadedRecordFileHash()).thenReturn("123");
        when(applicationStatus.getBypassRecordHashMismatchUntilAfter()).thenReturn("2019-07-02T00:00:00.000000Z.rcd");
        fileCopier.copy();
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .allMatch(p -> Utility.isRecordFile(p.toString()));
    }

    @Test
    @DisplayName("Invalid or incomplete record file")
    void invalidRecord() throws Exception {
        fileCopier.copy();
        Files.walk(s3Path).filter(p -> Utility.isRecordFile(p.toString())).forEach(RecordFileDownloaderTestV1::corruptFile);
        downloader.download();
        assertThat(Files.walk(validPath))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(0);
    }

    @Test
    @DisplayName("Error moving record to valid folder")
    void errorMovingFile() throws Exception {
        fileCopier.copy();
        validPath.toFile().delete();
        downloader.download();
        assertThat(validPath).doesNotExist();
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
