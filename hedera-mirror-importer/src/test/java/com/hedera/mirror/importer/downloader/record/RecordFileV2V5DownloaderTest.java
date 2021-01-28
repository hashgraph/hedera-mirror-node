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
import java.util.List;
import java.util.Map;

import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.domain.RecordFile;

// test v2 record file followed by a v5 record file, the start object running hash in v5 record file should match the
// file hash of the last v2 record file
class RecordFileV2V5DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        List<RecordFile> recordFiles = TestRecordFiles.getV2V5Files();
        RecordFile recordFileV2 = recordFiles.get(0);
        RecordFile recordFileV5 = recordFiles.get(1);
        return Map.of(recordFileV2.getName(), recordFileV2, recordFileV5.getName(), recordFileV5);
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(232L);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v2v5");
    }
}
