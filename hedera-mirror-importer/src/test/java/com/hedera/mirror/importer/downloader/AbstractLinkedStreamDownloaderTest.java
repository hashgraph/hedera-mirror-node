package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

// Common tests for streams (record and events) which are linked by previous file's hash.
@ExtendWith(MockitoExtension.class)
public abstract class AbstractLinkedStreamDownloaderTest extends AbstractDownloaderTest {

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() throws Exception {
        doReturn("2019-01-01T01:00:00.000000Z.rcd").when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileKey());
        doReturn("123").when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileHashKey());
        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        assertNoFilesinValidPath();
    }

    @Test
    @DisplayName("Bypass previous hash mismatch")
    void hashMismatchWithBypass() throws Exception {
        doReturn("2019-01-01T14:12:00.000000Z.rcd").when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileKey());
        doReturn("123").when(applicationStatusRepository)
                .findByStatusCode(downloader.getLastValidDownloadedFileHashKey());
        downloaderProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2050-01-01T00:00:00.000000Z"));
        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        assertValidFiles(List.of(file2));
    }
}
