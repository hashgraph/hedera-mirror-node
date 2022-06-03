package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.util.Utility;

class CryptoUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoUpdateTransactionHandler(entityIdService, entityListener, new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void updateTransactionStakedAccountId() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(1L).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder.cryptoUpdate()
                .transactionBody(body -> body.clear().setStakedAccountId(accountId))
                .build();
        setupForCrytoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(1L, Entity::getStakedAccountId)
                .returns(false, Entity::isDeclineReward)
                .returns(-1L, Entity::getStakedNodeId)
                .returns(Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart)
        );
    }

    @Test
    void updateTransactionDeclineReward() {
        RecordItem withDeclineValueSet = recordItemBuilder.cryptoUpdate()
                .transactionBody(body -> body.clear()
                        .setDeclineReward(BoolValue.of(true))
                )
                .build();
        setupForCrytoUpdateTransactionTest(withDeclineValueSet, t -> assertThat(t)
                .returns(true, Entity::isDeclineReward)
                // in this case both are still null, because we are not saving it into the database.
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .extracting(Entity::getStakePeriodStart)
                .isNotNull());
    }

    @Test
    void updateTransactionStakedNodeId() {
        RecordItem withStakedNodeIdSet = recordItemBuilder.cryptoUpdate()
                .transactionBody(body -> body.setStakedNodeId(1L))
                .build();
        setupForCrytoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(1L, Entity::getStakedNodeId)
                .returns(-1L, Entity::getStakedAccountId)
                .returns(true, Entity::isDeclineReward)
                .extracting(Entity::getStakePeriodStart)
                .isNotNull());
    }

    private void assertCryptoUpdate(long timestamp, Consumer<Entity> extraAssert) {
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertAll(
                () -> assertThat(t)
                        .isNotNull()
                        .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                        .returns(ACCOUNT, Entity::getType),
                () -> extraAssert.accept(t)
        )));
    }

    private void setupForCrytoUpdateTransactionTest(RecordItem recordItem, Consumer<Entity> extraAssertions) {
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        getTransactionHandler().updateTransaction(transaction, recordItem);
        assertCryptoUpdate(timestamp, extraAssertions);
    }
}
