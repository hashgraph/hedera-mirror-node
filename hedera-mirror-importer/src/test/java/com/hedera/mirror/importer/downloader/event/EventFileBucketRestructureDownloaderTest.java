/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.downloader.AbstractBucketRestructureDownloaderTest;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.reader.event.EventFileReaderV3;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventFileBucketRestructureDownloaderTest extends AbstractBucketRestructureDownloaderTest {

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(List.of(
                "2020-04-11T04_51_35.001934Z.evts",
                "2020-04-11T04_51_40.471976Z.evts",
                "2020-04-11T04_51_45.028997Z.evts",
                "2020-04-11T04_51_50.017047Z.evts"));
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
        return Paths.get("eventsStreams", "v3.accountId");
    }

    @Test
    @DisplayName("Download and verify files from new bucket in Auto Mode")
    void downloadFilesFromNewPathAutoMode() {
        // Changing bucket Path
        commonDownloaderProperties.setPathType(CommonDownloaderProperties.PathType.AUTO);
        // Reducing the pathRefresh interval to realistically test the node_id based path
        commonDownloaderProperties.setPathRefreshInterval(Duration.ofMillis(0L));
        fileCopier
                .from(getTestDataDir())
                .to(commonDownloaderProperties.getBucketName(), streamType.getPath())
                .copy();
        fileCopier
                .from(testnet)
                .to(commonDownloaderProperties.getBucketName(), testnet.toString())
                .copy();

        expectLastStreamFile(Instant.EPOCH);
        downloader.download();
        verifyStreamFiles(List.of(file1, file2));
        expectLastStreamFile(file2Instant);
        downloader.download();
        verifyStreamFiles(List.of(file1, file2, file3, file4));
        expectLastStreamFile(file4Instant);
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }
}
