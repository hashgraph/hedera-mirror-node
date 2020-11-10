package com.hedera.mirror.importer.downloader.record;

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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
public class RecordFileDownloaderTest extends AbstractLinkedStreamDownloaderTest {

    @Mock
    private RecordFileRepository recordFileRepository;

    @Captor
    private ArgumentCaptor<RecordFile> valueCaptor;

    private final Map<String, RecordFile> recordFileMap = new HashMap<>();

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader getDownloader() {
        return new RecordFileDownloader(s3AsyncClient, applicationStatusRepository, addressBookService,
                (RecordDownloaderProperties) downloaderProperties, transactionTemplate, meterRegistry,
                recordFileRepository);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v2");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }

    @Override
    protected void setDownloaderBatchSize(DownloaderProperties downloaderProperties, int batchSize) {
        RecordDownloaderProperties properties = (RecordDownloaderProperties) downloaderProperties;
        properties.setBatchSize(batchSize);
    }

    @Override
    protected void reset() {
        Mockito.reset(recordFileRepository);
        valueCaptor = ArgumentCaptor.forClass(RecordFile.class);
    }

    @Override
    protected void verifyStreamFileRecord(List<String> files) {
        verify(recordFileRepository, times(files.size())).save(valueCaptor.capture());
        List<RecordFile> captured = valueCaptor.getAllValues();
        assertThat(captured).hasSize(files.size()).allSatisfy(actual -> {
            RecordFile expected = recordFileMap.get(actual.getName());

            assertThat(actual).isEqualToIgnoringGivenFields(expected, "id", "loadStart", "loadEnd", "nodeAccountId",
                    "recordFormatVersion");
            assertThat(actual.getNodeAccountId()).isIn(allNodeAccountIds).isNotEqualTo(corruptedNodeAccountId);
        });
    }

    @BeforeEach
    void beforeEach() {
        setTestFilesAndInstants(
                "2019-08-30T18_10_00.419072Z.rcd",
                "2019-08-30T18_10_05.249678Z.rcd"
        );

        RecordFile rf1 = new RecordFile(1567188600419072000L, 1567188604906443001L, null, file1, 0L, 0L,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                null, 19L, 2);

        RecordFile rf2 = new RecordFile(1567188605249678000L, 1567188609705382001L, null, file2, 0L, 0L,
                "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36",
                rf1.getFileHash(), null, 15L, 2);

        recordFileMap.put(rf1.getName(), rf1);
        recordFileMap.put(rf2.getName(), rf2);
    }

    @Test
    @DisplayName("Download and verify V1 files")
    void downloadV1() throws Exception {
        doReturn(loadAddressBook("test-v1")).when(addressBookService).getCurrent();

        fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath(), "v1")
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());
        fileCopier.copy();

        prepareDownloader().download();

        verify(applicationStatusRepository).updateStatusValue(
                ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-07-01T14:13:00.317763Z.rcd");
        verify(applicationStatusRepository).updateStatusValue(
                ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE, "2019-07-01T14:29:00.302068Z.rcd");
        verify(applicationStatusRepository, times(2)).updateStatusValue(
                eq(ApplicationStatusCode.LAST_VALID_DOWNLOADED_RECORD_FILE_HASH), any());
        assertValidFiles(List.of("2019-07-01T14:13:00.317763Z.rcd", "2019-07-01T14:29:00.302068Z.rcd"));
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        ((RecordDownloaderProperties) downloaderProperties).setBatchSize(1);
        testMaxDownloadItemsReached(file1);
    }
}
