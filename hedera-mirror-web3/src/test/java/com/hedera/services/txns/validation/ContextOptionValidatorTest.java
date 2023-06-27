/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextOptionValidatorTest {

    @Mock
    MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private ContextOptionValidator subject;

    @BeforeEach
    void setup() {
        subject = new ContextOptionValidator(mirrorNodeEvmProperties);
    }

    @Test
    void rejectsInvalidMintBatchSize() {
        given(mirrorNodeEvmProperties.getMaxBatchSizeMint()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxBatchSizeMintCheck(12));
    }

    @Test
    void rejectsInvalidMetadata() {
        given(mirrorNodeEvmProperties.getMaxNftMetadataBytes()).willReturn(2);
        assertEquals(METADATA_TOO_LONG, subject.nftMetadataCheck(new byte[] {1, 2, 3, 4}));
    }
}
