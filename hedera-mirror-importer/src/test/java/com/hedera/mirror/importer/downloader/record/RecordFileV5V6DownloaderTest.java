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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.TestRecordFiles;

public class RecordFileV5V6DownloaderTest  extends AbstractRecordFileDownloaderTest {

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
        return Duration.ofSeconds(158L);
    }

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        List<RecordFile> recordFiles = TestRecordFiles.getV5V6Files();
        RecordFile recordFileV2 = recordFiles.get(0);
        RecordFile recordFileV5 = recordFiles.get(1);
        return Map.of(recordFileV2.getName(), recordFileV2, recordFileV5.getName(), recordFileV5);
    }
}
