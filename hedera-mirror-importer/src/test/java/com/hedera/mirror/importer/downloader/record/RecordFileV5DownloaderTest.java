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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;

public class RecordFileV5DownloaderTest extends AbstractRecordFileDownloaderTest {

    @BeforeAll
    static void beforeAll() throws IOException {
        addressBook = loadAddressBook("previewnet");
        allNodeAccountIds = addressBook.getNodeSet();
    }

    @Override
    protected List<String> getTestFiles() {
        return List.of("2021-01-11T22_09_24.063739000Z.rcd", "2021-01-11T22_09_34.097416003Z.rcd");
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
