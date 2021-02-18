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

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.reader.record.CompositeRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV1;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV2;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV5;

abstract class AbstractRecordFileDownloaderTest extends AbstractLinkedStreamDownloaderTest {

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
        return new RecordFileDownloader(s3AsyncClient, addressBookService,
                (RecordDownloaderProperties) downloaderProperties, meterRegistry,
                nodeSignatureVerifier, signatureFileReader, recordFileReader, streamFileNotifier, dateRangeProcessor);
    }
}
