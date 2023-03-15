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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;

class ConsensusUpdateTopicTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusUpdateTopicTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOPIC;
    }

    @Test
    void updateTransactionUnsuccessful() {
        var recordItem = recordItemBuilder.consensusUpdateTopic()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)).build();
        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.consensusUpdateTopic().build();
        var topicId = EntityId.of(recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(topicId)).get();
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertConsensusTopicUpdate(timestamp, topicId, id -> assertEquals(10L, id));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.consensusUpdateTopic()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var topicId = EntityId.of(recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(topicId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertConsensusTopicUpdate(timestamp, topicId, id -> assertEquals(10L, id));
    }

    @Test
    void updateTransactionEntityIdEmptyNull() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.consensusUpdateTopic()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var topicId = EntityId.of(recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(topicId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build())).thenReturn(EntityId.EMPTY);

        transactionHandler.updateTransaction(transaction, recordItem);
        assertConsensusTopicUpdate(timestamp, topicId, id -> assertEquals(0, id));
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountId() {
        var recordItem = recordItemBuilder.consensusUpdateTopic()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAccountNum(0))
                .build();
        var topicId = EntityId.of(recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(topicId)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertConsensusTopicUpdate(timestamp, topicId, id -> assertEquals(0L, id));
    }

    @SuppressWarnings("java:S6103")
    private void assertConsensusTopicUpdate(long timestamp, EntityId topicId, Consumer<Long> assertAutoRenewAccountId) {
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(e -> assertAutoRenewAccountId.accept(e.getAutoRenewAccountId()))
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(topicId.getId(), Entity::getId)
                .returns(topicId.getEntityNum(), Entity::getNum)
                .returns(topicId.getRealmNum(), Entity::getRealm)
                .returns(topicId.getShardNum(), Entity::getShard)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(TOPIC, Entity::getType)
        ));
    }
}
