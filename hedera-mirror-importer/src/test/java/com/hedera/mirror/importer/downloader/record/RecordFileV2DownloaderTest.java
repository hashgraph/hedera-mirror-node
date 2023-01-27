package com.hedera.mirror.importer.downloader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.TestUtils;

class RecordFileV2DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        RecordFile recordFile1 = allRecordFileMap.get("2019-08-30T18_10_00.419072Z.rcd");
        RecordFile recordFile2 = allRecordFileMap.get("2019-08-30T18_10_05.249678Z.rcd");
        return Map.of(recordFile1.getName(), recordFile1, recordFile2.getName(), recordFile2);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v2");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }

    @Test
    @DisplayName("Download and verify V1 files")
    void downloadV1() {
        mirrorProperties.setStartBlockNumber(null);
        loadAddressBook("test-v1");
        var allRecordFiles = TestRecordFiles.getAll();
        var testRecordFiles = Map.of(
                "2019-07-01T14_13_00.317763Z.rcd", allRecordFiles.get("2019-07-01T14_13_00.317763Z.rcd"),
                "2019-07-01T14_29_00.302068Z.rcd", allRecordFiles.get("2019-07-01T14_29_00.302068Z.rcd")
        );
        setupRecordFiles(testRecordFiles);

        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath(), "v1")
                .to(commonDownloaderProperties.getBucketName(), downloaderProperties.getStreamType().getPath());
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyForSuccess();
    }
}
