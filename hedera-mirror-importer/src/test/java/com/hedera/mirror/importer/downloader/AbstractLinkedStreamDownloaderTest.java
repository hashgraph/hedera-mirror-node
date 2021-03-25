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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cglib.core.ReflectUtils;

import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.util.Utility;

// Common tests for streams (record and events) which are linked by previous file's hash.
public abstract class AbstractLinkedStreamDownloaderTest extends AbstractDownloaderTest {

    @Test
    @DisplayName("Doesn't match last valid hash")
    void hashMismatchWithPrevious() {
        expectLastStreamFile("123", 1L, Instant.EPOCH.plusNanos(100L));

        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        verifyUnsuccessful();
    }

    @Test
    @DisplayName("Bypass previous hash mismatch")
    void hashMismatchWithBypass() {
        expectLastStreamFile("123", 1L, Instant.EPOCH.plusNanos(100L));

        downloaderProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2050-01-01T00:00:00.000000Z"));
        fileCopier.filterFiles(file2 + "*").copy(); // Skip first file with zero hash
        downloader.download();
        verifyStreamFiles(List.of(file2));
    }

    @ParameterizedTest(name = "verifyHashChain {5}")
    @CsvSource({
            // @formatter:off
            "'', '', 1970-01-01T00:00:00Z,        2000-01-01T10:00:00Z, true,  passes if both hashes are empty",
            "xx, '', 1970-01-01T00:00:00Z,        2000-01-01T10:00:00Z, true,  passes if hash mismatch and expected hash is empty", // starting stream in middle
            "'', xx, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00Z, false, fails if hash mismatch and actual hash is empty", // bad db state
            "xx, yy, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00Z, false, fails if hash mismatch and hashes are non-empty",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10:00:00Z, true,  passes if hash mismatch but verifyHashAfter is after filename",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10:00:00Z, true,  passes if hash mismatch but verifyHashAfter is same as filename",
            "xx, yy, 2000-01-01T09:59:59.999999Z, 2000-01-01T10:00:00Z, false, fails if hash mismatch and verifyHashAfter is before filename",
            "xx, xx, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00Z, true,  passes if hashes are equal"
            // @formatter:on
    })
    void verifyHashChain(String actualPrevFileHash, String expectedPrevFileHash,
            Instant verifyHashAfter, Instant fileInstant,
            Boolean expectedResult, String testName) {
        downloaderProperties.getMirrorProperties().setVerifyHashAfter(verifyHashAfter);
        StreamFile streamFile = (StreamFile) ReflectUtils.newInstance(streamType.getStreamFileClass());
        streamFile.setConsensusStart(Utility.convertToNanosMax(fileInstant));
        streamFile.setName(StreamFilename.getFilename(streamType, StreamFilename.FileType.DATA, fileInstant));
        streamFile.setPreviousHash(actualPrevFileHash);
        assertThat(downloader.verifyHashChain(streamFile, expectedPrevFileHash))
                .as(testName)
                .isEqualTo(expectedResult);
    }
}
