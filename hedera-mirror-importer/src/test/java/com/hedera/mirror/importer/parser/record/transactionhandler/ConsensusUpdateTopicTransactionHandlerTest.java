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
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ConsensusUpdateTopicTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusUpdateTopicTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder()
                                .setTopicNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOPIC;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.consensusUpdateTopic().build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var autoRenewAccountId = EntityId.of(10L, ACCOUNT);
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction, autoRenewAccountId);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(autoRenewAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertConsensusTopicUpdate(timestamp, topicId, autoRenewAccountId.getId());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.setAutoRenewAccount(aliasAccountId))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var autoRenewAccountId = EntityId.of(10L, ACCOUNT);
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction, autoRenewAccountId);
        when(entityIdService.lookup(aliasAccountId)).thenReturn(Optional.of(autoRenewAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertConsensusTopicUpdate(timestamp, topicId, autoRenewAccountId.getId());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionEntityIdEmpty(EntityId entityId) {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.setAutoRenewAccount(aliasAccountId))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        when(entityIdService.lookup(aliasAccountId)).thenReturn(Optional.ofNullable(entityId));
        var expectedId = entityId == null ? null : entityId.getId();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertConsensusTopicUpdate(timestamp, topicId, expectedId);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountId() {
        // given
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAccountNum(0))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertConsensusTopicUpdate(timestamp, topicId, 0L);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    private void assertConsensusTopicUpdate(long timestamp, EntityId topicId, Long expectedAutoRenewAccountId) {
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(topicId.getId(), Entity::getId)
                .returns(topicId.getEntityNum(), Entity::getNum)
                .returns(topicId.getRealmNum(), Entity::getRealm)
                .returns(topicId.getShardNum(), Entity::getShard)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(TOPIC, Entity::getType)));
    }
}
