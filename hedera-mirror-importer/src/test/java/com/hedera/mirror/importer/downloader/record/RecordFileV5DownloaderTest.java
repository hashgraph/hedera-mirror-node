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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.domain.RecordFile;

class RecordFileV5DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        RecordFile recordFile1 = allRecordFileMap.get("2021-01-11T22_09_24.063739000Z.rcd");
        RecordFile recordFile2 = allRecordFileMap.get("2021-01-11T22_09_34.097416003Z.rcd");
        return Map.of(recordFile1.getName(), recordFile1, recordFile2.getName(), recordFile2);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v5");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(10L);
    }
}
