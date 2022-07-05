package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.*;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.importer.repository.PrngRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerUtilTest extends AbstractEntityRecordItemListenerTest {

    private final PrngRepository prngRepository;

    @Test
    void prngUpdateRandomNumber() {
        var recordItem = recordItemBuilder.prng(Integer.MAX_VALUE).build();
        int pseudorandomNumber = recordItem.getRecord().getPrngNumber();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, prngRepository.count()),
                () -> assertThat(prngRepository.findAll())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), Prng::getConsensusTimestamp)
                        .returns(Integer.MAX_VALUE, Prng::getRange)
                        .returns(pseudorandomNumber, Prng::getPrngNumber)
                        .returns(null, Prng::getPrngBytes)
        );
    }

    @Test
    void prngUpdateRandomBytes() {
        var recordItem = recordItemBuilder.prng(0).build();
        byte[] pseudorandomBytes = recordItem.getRecord().getPrngBytes().toByteArray();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, prngRepository.count()),
                () -> assertThat(prngRepository.findAll())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), Prng::getConsensusTimestamp)
                        .returns(0, Prng::getRange)
                        .returns(pseudorandomBytes, Prng::getPrngBytes)
                        .returns(null, Prng::getPrngNumber)
        );
    }
}
