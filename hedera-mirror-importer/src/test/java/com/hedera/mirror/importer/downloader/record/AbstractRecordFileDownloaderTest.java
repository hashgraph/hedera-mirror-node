package com.hedera.mirror.importer.downloader.record;

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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.reader.record.CompositeRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV1;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV2;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV5;
import com.hedera.mirror.importer.repository.RecordFileRepository;

abstract class AbstractRecordFileDownloaderTest extends AbstractLinkedStreamDownloaderTest {

    @Mock
    private RecordFileRepository recordFileRepository;

    @Captor
    private ArgumentCaptor<RecordFile> valueCaptor;

    private Map<String, RecordFile> recordFileMap;

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        recordFileMap = getRecordFileMap();
        setTestFilesAndInstants(recordFileMap.keySet().stream().sorted().collect(Collectors.toList()));
    }

    abstract protected Map<String, RecordFile> getRecordFileMap();

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
    }

    @Override
    protected Downloader getDownloader() {
        RecordFileReader recordFileReader = new CompositeRecordFileReader(new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(), new RecordFileReaderImplV5());
        return new RecordFileDownloader(s3AsyncClient, applicationStatusRepository, addressBookService,
                (RecordDownloaderProperties) downloaderProperties, transactionTemplate, meterRegistry,
                recordFileReader, recordFileRepository, nodeSignatureVerifier, signatureFileReader);
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

            assertThat(actual).isEqualToIgnoringGivenFields(expected, "id", "loadStart", "loadEnd", "nodeAccountId");
            assertThat(actual.getNodeAccountId()).isIn(allNodeAccountIds).isNotEqualTo(corruptedNodeAccountId);
        });
    }
}
