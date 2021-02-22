package com.hedera.mirror.importer.downloader;

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

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.RecordFile;

// Common tests for streams (record and events) which are linked by previous file's hash.
public abstract class AbstractLinkedStreamDownloaderTest extends AbstractDownloaderTest {

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() throws Exception {
        expectLastStreamFile("123", 1L, Instant.EPOCH.plusNanos(100L), true);

        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Bypass previous hash mismatch")
    void hashMismatchWithBypass() {
        expectLastStreamFile("123", 1L, Instant.EPOCH.plusNanos(100L), true);

        downloaderProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2050-01-01T00:00:00.000000Z"));
        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        verifyStreamFiles(List.of(file2));
    }
}
