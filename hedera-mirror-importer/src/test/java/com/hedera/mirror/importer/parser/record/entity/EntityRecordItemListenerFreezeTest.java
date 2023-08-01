/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NetworkFreezeRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerFreezeTest extends AbstractEntityRecordItemListenerTest {

    private final NetworkFreezeRepository networkFreezeRepository;

    @Test
    void freeze() {
        var recordItem = recordItemBuilder.freeze().build();
        var freeze = recordItem.getTransactionBody().getFreeze();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(transactionRepository.count()).isOne();
        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(3);
        softly.assertThat(networkFreezeRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NetworkFreeze::getConsensusTimestamp)
                .returns(null, NetworkFreeze::getEndTime)
                .returns(DomainUtils.toBytes(freeze.getFileHash()), NetworkFreeze::getFileHash)
                .returns(EntityId.of(freeze.getUpdateFile()), NetworkFreeze::getFileId)
                .returns(recordItem.getPayerAccountId(), NetworkFreeze::getPayerAccountId)
                .returns(DomainUtils.timestampInNanosMax(freeze.getStartTime()), NetworkFreeze::getStartTime)
                .returns(freeze.getFreezeTypeValue(), NetworkFreeze::getType);
    }

    @Test
    void freezeInvalidTransaction() {
        var recordItem = recordItemBuilder
                .freeze()
                .status(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(3);
        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(networkFreezeRepository.count()).isZero();
        softly.assertThat(transactionRepository.count()).isOne();
    }
}
