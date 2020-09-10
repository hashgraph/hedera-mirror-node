package com.hedera.mirror.importer.downloader.event;

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
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.reader.event.EventFileReaderImpl;

@ExtendWith(MockitoExtension.class)
public class EventFileDownloaderTest extends AbstractLinkedStreamDownloaderTest {

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        var eventDownloaderProperties = new EventDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        eventDownloaderProperties.setEnabled(true);
        return eventDownloaderProperties;
    }

    @Override
    protected Downloader getDownloader() {
        return new EventFileDownloader(s3AsyncClient, applicationStatusRepository, addressBookService,
                (EventDownloaderProperties) downloaderProperties, transactionTemplate, meterRegistry, new EventFileReaderImpl());
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("eventsStreams", "v3");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }

    @Override
    protected void setDownloaderBatchSize(DownloaderProperties downloaderProperties, int batchSize) {
        EventDownloaderProperties properties = (EventDownloaderProperties) downloaderProperties;
        properties.setBatchSize(batchSize);
    }

    @Override
    protected void resetStreamFileRepositoryMock() {
        // no-op, add the logic when event file is saved into db
    }

    @Override
    protected void verifyStreamFileRecord(List<String> files) {
        // no-op, add the logic when event file is saved into db
    }

    @Test
    @DisplayName("Max download items reached")
    void maxDownloadItemsReached() throws Exception {
        ((EventDownloaderProperties) downloaderProperties).setBatchSize(1);
        testMaxDownloadItemsReached(file1);
    }

    @BeforeEach
    void beforeEach() {
        setTestFilesAndInstants(
                "2020-04-11T00_12_00.025035Z.evts",
                "2020-04-11T00_12_05.059945Z.evts"
        );
    }
}
