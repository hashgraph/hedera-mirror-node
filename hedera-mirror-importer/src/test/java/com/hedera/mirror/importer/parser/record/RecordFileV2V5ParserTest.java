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

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;

// test v2 record file followed by a v5 record file, the start object running hash in v5 record file should match the
// file hash of the last v2 record file
class RecordFileV2V5ParserTest extends AbstractRecordFileParserTest {

    private final static EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);

    private final static long[] FILE_CONSENSUS_TIMESTAMPS = {
            1611188151568507001L,
            1611188383558496000L
    };

    private final static RecordFile RECORD_FILE_V2 = RecordFile.builder()
            .consensusStart(1611188151568507001L)
            .consensusEnd(1611188151568507001L)
            .count(1L)
            .digestAlgorithm(DigestAlgorithm.SHA384)
            .fileHash("e7d9e71efd239bde3adcad8eb0571c38f91f77ae76a4af69bb44f19b2785ad3594ac1d265351a592ab14301da9bb1950")
            .name("2021-01-21T00_15_51.568507001Z.rcd")
            .nodeAccountId(NODE_ACCOUNT_ID)
            .previousHash("d27ba83c736bfa2ffc9a6f062b27ea4856800bbbe820b77b32e08faf3d7475d81ef5a16f90ce065d35eefa999677edaa")
            .version(2)
            .build();

    private final static RecordFile RECORD_FILE_V5 =  RecordFile.builder()
            .consensusStart(1611188383558496000L)
            .consensusEnd(1611188383558496000L)
            .count(1L)
            .digestAlgorithm(DigestAlgorithm.SHA384)
            .endRunningHash("e6c1d7bfe956b6b2c8061bee5c43e512111cbccb21099bb0c49e2a7c74cf617cf5b6bf65070f29eb43a80d9cef2d8242")
            .fileHash("42717bae0e538bac34563784b08b5a5b50a9964c9435452c93134bf13355c9778a1c64cfdc30f33fe52dd7f76dbdda70")
            .hapiVersionMajor(0)
            .hapiVersionMinor(11)
            .hapiVersionPatch(0)
            .metadataHash("1d83206a166a06c8579f9de637cf50a565341928b55bfbdc774ce85ac2169b46c23db42729723e7c39e5a042bd9e3b98")
            .name("2021-01-21T00_19_43.558496000Z.rcd")
            .nodeAccountId(NODE_ACCOUNT_ID)
            .previousHash(RECORD_FILE_V2.getFileHash())
            .version(5)
            .build();

    RecordFileV2V5ParserTest() {
        super(RECORD_FILE_V2, RECORD_FILE_V5, FILE_CONSENSUS_TIMESTAMPS, "v2v5");
    }

    // static method to provide arguments for the parameterized tests in base class
    private static Stream<Arguments> provideTimeOffsetArgument() {
        return provideTimeOffsetArgumentFromRecordFile(RECORD_FILE_V2);
    }
}
