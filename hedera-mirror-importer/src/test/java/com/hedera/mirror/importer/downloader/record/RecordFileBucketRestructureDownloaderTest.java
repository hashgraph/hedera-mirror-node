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

package com.hedera.mirror.importer.downloader.record;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.downloader.AbstractBucketRestructureDownloaderTest;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import com.hedera.mirror.importer.reader.record.CompositeRecordFileReader;
import com.hedera.mirror.importer.reader.record.ProtoRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV1;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV2;
import com.hedera.mirror.importer.reader.record.RecordFileReaderImplV5;
import com.hedera.mirror.importer.reader.record.sidecar.SidecarFileReaderImpl;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordFileBucketRestructureDownloaderTest extends AbstractBucketRestructureDownloaderTest {

    private static final List<String> RECORD_FILE_NAME_ORDER = List.of(
            "2022-12-25T09_14_22.940899003Z.rcd.gz",
            "2022-12-25T09_14_24.197926003Z.rcd.gz",
            "2022-12-25T09_14_26.072307770Z.rcd.gz",
            "2022-12-25T09_14_28.278703292Z.rcd.gz");

    private static final Map<String, Long> RECORD_FILE_INDEX_MAP = Map.of(
            RECORD_FILE_NAME_ORDER.get(0), Long.valueOf(205325L),
            RECORD_FILE_NAME_ORDER.get(1), Long.valueOf(205326L),
            RECORD_FILE_NAME_ORDER.get(2), Long.valueOf(205327L),
            RECORD_FILE_NAME_ORDER.get(3), Long.valueOf(205328L));

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setTestFilesAndInstants(RECORD_FILE_NAME_ORDER);
    }

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        var recordDownloaderProperties = new RecordDownloaderProperties(mirrorProperties, commonDownloaderProperties);
        recordDownloaderProperties.setEnabled(true);
        return recordDownloaderProperties;
    }

    @Override
    protected Downloader<RecordFile, RecordItem> getDownloader() {

        var recordFileReader = new CompositeRecordFileReader(
                new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(),
                new RecordFileReaderImplV5(),
                new ProtoRecordFileReader());
        sidecarProperties = new SidecarProperties();
        sidecarProperties.setEnabled(true);
        var streamFileProvider = new S3StreamFileProvider(commonDownloaderProperties, s3AsyncClient);
        return new RecordFileDownloader(
                consensusNodeService,
                (RecordDownloaderProperties) downloaderProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                new SidecarFileReaderImpl(),
                sidecarProperties,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                recordFileReader);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v6.accountId");
    }

    @Override
    protected Map<String, Long> getExpectedFileIndexMap() {

        return RECORD_FILE_INDEX_MAP;
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
