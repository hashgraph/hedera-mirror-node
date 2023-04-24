/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.downloader.event;

import com.hedera.mirror.common.domain.event.EventFile;
import com.hedera.mirror.common.domain.event.EventItem;
import com.hedera.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.reader.event.EventFileReaderV3;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

class EventFileDownloaderTest extends AbstractLinkedStreamDownloaderTest<EventFile> {

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(List.of("2020-04-11T00_12_00.025035Z.evts", "2020-04-11T00_12_05.059945Z.evts"));
    }

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        var eventDownloaderProperties = new EventDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        eventDownloaderProperties.setEnabled(true);
        return eventDownloaderProperties;
    }

    @Override
    protected Downloader<EventFile, EventItem> getDownloader() {
        var streamFileProvider = new S3StreamFileProvider(commonDownloaderProperties, s3AsyncClient);
        return new EventFileDownloader(
                consensusNodeService,
                (EventDownloaderProperties) downloaderProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                new EventFileReaderV3());
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("eventsStreams", "v3");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }
}
