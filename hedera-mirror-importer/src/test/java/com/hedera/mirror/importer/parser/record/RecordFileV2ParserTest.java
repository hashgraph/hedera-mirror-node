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

class RecordFileV2ParserTest extends AbstractRecordFileParserTest {

    private final static String[] FILENAMES = {"2019-08-30T18_10_00.419072Z.rcd", "2019-08-30T18_10_05.249678Z.rcd"};
    private static final long[] FILE_CONSENSUS_TIMESTAMPS = {
            1567188600419072000L,
            1567188600762801000L,
            1567188600883799000L,
            1567188600963103000L,
            1567188601371995000L,
            1567188601739500000L,
            1567188602108299000L,
            1567188602118673001L,
            1567188602450554001L,
            1567188602838615000L,
            1567188603175332000L,
            1567188603277507000L,
            1567188603609988000L,
            1567188603706753001L,
            1567188604084005000L,
            1567188604429968000L,
            1567188604524835000L,
            1567188604856082001L,
            1567188604906443001L,
            1567188605249678000L,
            1567188605824917000L,
            1567188606171654000L,
            1567188606181740000L,
            1567188606499404000L,
            1567188606576024000L,
            1567188606932283000L,
            1567188607246509000L,
            1567188608065015001L,
            1567188608413240000L,
            1567188608566437000L,
            1567188608878373000L,
            1567188608972069001L,
            1567188609337810002L,
            1567188609705382001L
    };

    RecordFileV2ParserTest() {
        super(FILENAMES[0], FILENAMES[1], FILE_CONSENSUS_TIMESTAMPS);
    }

    // static method to provide arguments for the parameterized tests in base class
    private static Stream<Arguments> provideTimeOffsetArgument() {
        return provideTimeOffsetArgumentFromRecordFile(FILENAMES[0]);
    }
}
