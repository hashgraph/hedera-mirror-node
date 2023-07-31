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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ConsensusDeleteTopicTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusDeleteTopicTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.newBuilder()
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
        var recordItem = recordItemBuilder.consensusDeleteTopic().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusDeleteTopic().getTopicID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntity = topicId.toEntity().toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(timestamp))
                .build();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
