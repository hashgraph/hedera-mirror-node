package com.hedera.mirror.importer.parser.record;

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

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.domain.RecordFile;

// test v2 record file followed by a v5 record file, the start object running hash in v5 record file should match the
// file hash of the last v2 record file
class RecordFileV2V5ParserTest extends AbstractRecordFileParserTest {

    private final static long[] FILE_CONSENSUS_TIMESTAMPS = {
            1611188151568507001L,
            1611188383558496000L
    };

    private final static List<RecordFile> RECORD_FILES = TestRecordFiles.getV2V5Files();

    RecordFileV2V5ParserTest() {
        super(RECORD_FILES.get(0), RECORD_FILES.get(1), FILE_CONSENSUS_TIMESTAMPS, "v2v5");
    }

    // static method to provide arguments for the parameterized tests in base class
    private static Stream<Arguments> provideTimeOffsetArgument() {
        return provideTimeOffsetArgumentFromRecordFile(RECORD_FILES.get(0));
    }
}
