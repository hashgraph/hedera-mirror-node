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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CryptoUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoUpdateTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(AccountID.newBuilder()
                                .setAccountNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionDeclineReward(Boolean declineReward) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(body -> body.clear().setDeclineReward(BoolValue.of(declineReward)))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(declineReward, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 100})
    void updateTransactionStakedAccountId(long accountNum) {
        // Note, the sentinel value '0.0.0' clears the staked account id, in importer, we persist the encoded id '0' to
        // db to indicate there is no staked account id
        AccountID accountId = AccountID.newBuilder().setAccountNum(accountNum).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(body -> body.clear().setStakedAccountId(accountId))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(null, Entity::getDeclineReward)
                .returns(accountNum, Entity::getStakedAccountId)
                .returns(-1L, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, -1})
    void updateTransactionStakedNodeId(Long nodeId) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(body -> body.setStakedNodeId(nodeId))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(0L, Entity::getStakedAccountId)
                .returns(true, Entity::getDeclineReward)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    private void assertCryptoUpdate(long timestamp, Consumer<Entity> extraAssert) {
        verify(entityListener, times(1))
                .onEntity(assertArg(t -> assertAll(
                        () -> assertThat(t)
                                .isNotNull()
                                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                                .returns(ACCOUNT, Entity::getType),
                        () -> extraAssert.accept(t))));
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getCryptoUpdateAccount();
        return getExpectedEntityTransactions(
                recordItem, transaction, EntityId.of(body.getStakedAccountId()), EntityId.of(body.getProxyAccountID()));
    }

    private void setupForCryptoUpdateTransactionTest(RecordItem recordItem, Consumer<Entity> extraAssertions) {
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        getTransactionHandler().updateTransaction(transaction, recordItem);
        assertCryptoUpdate(timestamp, extraAssertions);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
