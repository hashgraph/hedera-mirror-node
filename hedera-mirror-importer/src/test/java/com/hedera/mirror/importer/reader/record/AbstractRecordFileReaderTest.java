/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.record;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

abstract class AbstractRecordFileReaderTest extends RecordFileReaderTest {

    @TestFactory
    Stream<DynamicTest> readIncompatibleFile() {
        String template = "read incompatible version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(true),
                (recordFile) -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                (recordFile) -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }
}
