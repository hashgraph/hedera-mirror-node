package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

class ScheduleCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private EntityProperties entityProperties;

    @Override
    protected TransactionHandler getTransactionHandler() {
        entityProperties = new EntityProperties();
        return new ScheduleCreateTransactionHandler(entityIdService, entityListener, entityProperties,
                new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        SchedulableTransactionBody.Builder scheduledTransactionBodyBuilder = SchedulableTransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
        return TransactionBody.newBuilder()
                .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(DEFAULT_KEY)
                        .setPayerAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1)
                                .build())
                        .setMemo("schedule memo")
                        .setScheduledTransactionBody(scheduledTransactionBodyBuilder)
                        .build());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum)
                .setScheduleID(ScheduleID.newBuilder().setScheduleNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.SCHEDULE;
    }

    @Test
    void updateTransactionSchedulesDisabled() {
        // given
        entityProperties.getPersist().setSchedules(false);
        var recordItem = recordItemBuilder.scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId())).build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)
        ));
        verify(entityListener, never()).onSchedule(any());
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId())).build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)
        ));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .satisfies(s -> assertThat(s.getExpirationTime()).isPositive())
                .satisfies(s -> assertThat(s.getPayerAccountId()).isNotNull())
                .satisfies(s -> assertThat(s.getScheduleId()).isPositive())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(true, Schedule::isWaitForExpiry)
        ));
    }

    @Test
    void updateTransactionSuccessfulNoExpirationTime() {
        // given
        var recordItem = recordItemBuilder.scheduleCreate()
                .transactionBody(b -> b.clearExpirationTime().clearPayerAccountID().setWaitForExpiry(false))
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId())).build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)
        ));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .returns(null, Schedule::getExpirationTime)
                .returns(recordItem.getPayerAccountId(), Schedule::getPayerAccountId)
                .satisfies(s -> assertThat(s.getScheduleId()).isPositive())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(false, Schedule::isWaitForExpiry)
        ));
    }

    @Test
    void updateTransactionUnsuccessful() {
        // given
        var recordItem = recordItemBuilder.scheduleCreate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)).build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }
}
