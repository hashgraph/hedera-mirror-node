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

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class RecordFileV5ParserTest extends AbstractRecordFileParserTest {

    private final static String[] FILENAMES = {
            "2021-01-11T22_09_24.063739000Z.rcd",
            "2021-01-11T22_09_34.097416003Z.rcd"
    };
    private final static long[] FILE_CONSENSUS_TIMESTAMPS = {
            1610402964063739000L,
            1610402974097416003L
    };

    RecordFileV5ParserTest() {
        super(FILENAMES[0], FILENAMES[1], FILE_CONSENSUS_TIMESTAMPS);
    }

    // static method to provide arguments for the parameterized tests in base class
    private static Stream<Arguments> provideTimeOffsetArgument() {
        return provideTimeOffsetArgumentFromRecordFile(FILENAMES[0]);
    }
}
