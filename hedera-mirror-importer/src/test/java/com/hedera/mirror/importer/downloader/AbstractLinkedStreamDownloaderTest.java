/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFilename;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Common tests for streams (record and events) which are linked by previous file's hash.
public abstract class AbstractLinkedStreamDownloaderTest<T extends StreamFile<?>> extends AbstractDownloaderTest<T> {

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() {
        expectLastStreamFile("123", 1L, Instant.EPOCH.plusNanos(100L));

        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        verifyUnsuccessful();
    }

    @ParameterizedTest(name = "verifyHashChain {4}")
    @CsvSource(
            textBlock =
                    """
                '', '', 1970-01-01T00:00:00Z, true,  passes if both hashes are empty",
                xx, '', 1970-01-01T00:00:00Z, true,  passes if hash mismatch and expected hash is empty
                '', xx, 1970-01-01T00:00:00Z, false, fails if hash mismatch and actual hash is empty
                xx, yy, 1970-01-01T00:00:00Z, false, fails if hash mismatch and hashes are non-empty
                xx, xx, 1970-01-01T00:00:00Z, true,  passes if hashes are equal
            """)
    void verifyHashChain(
            String actualPrevFileHash,
            String expectedPrevFileHash,
            Instant fileInstant,
            Boolean expectedResult,
            String testName) {
        T streamFile = streamType.newStreamFile();
        streamFile.setConsensusStart(DomainUtils.convertToNanosMax(fileInstant));
        streamFile.setName(StreamFilename.getFilename(streamType, StreamFilename.FileType.DATA, fileInstant));
        streamFile.setPreviousHash(actualPrevFileHash);
        assertThat(downloader.verifyHashChain(streamFile, expectedPrevFileHash))
                .as(testName)
                .isEqualTo(expectedResult);
    }
}
