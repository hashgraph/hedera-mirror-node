/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Test;

class ScheduleCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ScheduleCreateTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        SchedulableTransactionBody.Builder scheduledTransactionBodyBuilder = SchedulableTransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
        return TransactionBody.newBuilder()
                .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(DEFAULT_KEY)
                        .setPayerAccountID(AccountID.newBuilder()
                                .setShardNum(0)
                                .setRealmNum(0)
                                .setAccountNum(1)
                                .build())
                        .setMemo("schedule memo")
                        .setScheduledTransactionBody(scheduledTransactionBodyBuilder)
                        .build());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setScheduleID(ScheduleID.newBuilder()
                        .setScheduleNum(DEFAULT_ENTITY_NUM)
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.SCHEDULE;
    }

    @Test
    void updateTransactionSchedulesDisabled() {
        // given
        entityProperties.getPersist().setSchedules(false);
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, never()).onSchedule(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        var schedulePayerAccountId =
                EntityId.of(recordItem.getTransactionBody().getScheduleCreate().getPayerAccountID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .satisfies(s -> assertThat(s.getExpirationTime()).isPositive())
                .returns(schedulePayerAccountId, Schedule::getPayerAccountId)
                .satisfies(s -> assertThat(s.getScheduleId()).isPositive())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(true, Schedule::isWaitForExpiry)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, schedulePayerAccountId));
    }

    @Test
    void updateTransactionSuccessfulNoExpirationTime() {
        // given
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .transactionBody(
                        b -> b.clearExpirationTime().clearPayerAccountID().setWaitForExpiry(false))
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var payerAccountId = recordItem.getPayerAccountId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .returns(null, Schedule::getExpirationTime)
                .returns(payerAccountId, Schedule::getPayerAccountId)
                .satisfies(s -> assertThat(s.getScheduleId()).isPositive())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(false, Schedule::isWaitForExpiry)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, payerAccountId));
    }
}
