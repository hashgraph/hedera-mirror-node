package com.hedera.mirror.importer.downloader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.TestRecordFiles;

class RecordFileV5V6DownloaderTest extends AbstractRecordFileDownloaderTest {

    private static final RecordFile recordFileV5 = TestRecordFiles.getV5V6Files().get(0);
    private static final RecordFile recordFileV6 = TestRecordFiles.getV5V6Files().get(1);

    @BeforeAll
    static void beforeAll() throws IOException {
        addressBook = loadAddressBook("test-v6-4n.bin");
        allNodeAccountIds = addressBook.getNodeSet();
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v5v6");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(64L);
    }

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        return Map.of(recordFileV5.getName(), recordFileV5, recordFileV6.getName(), recordFileV6);
    }

    @Override
    protected Map<String, Long> getExpectedFileIndexMap() {
        return Map.of(recordFileV6.getName(), recordFileV6.getIndex());
    }
}
