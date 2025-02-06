/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.topic.Topic;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;

@Named
class ConsensusUpdateTopicTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ConsensusUpdateTopicTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CONSENSUSUPDATETOPIC);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var transactionBody = recordItem.getTransactionBody().getConsensusUpdateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            // Allow clearing of the autoRenewAccount by allowing it to be set to 0
            entityIdService
                    .lookup(transactionBody.getAutoRenewAccount())
                    .ifPresentOrElse(
                            entityId -> {
                                entity.setAutoRenewAccountId(entityId.getId());
                                recordItem.addEntityId(entityId);
                            },
                            () -> Utility.handleRecoverableError(
                                    "Invalid autoRenewAccountId at {}", consensusTimestamp));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            Timestamp expirationTime = transactionBody.getExpirationTime();
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(expirationTime));
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        entity.setType(EntityType.TOPIC);
        entityListener.onEntity(entity);

        updateTopic(consensusTimestamp, entity.getId(), transactionBody);

        if (transactionBody.hasCustomFees()) {
            updateCustomFee(transactionBody.getCustomFees().getFeesList(), recordItem, entity.getId());
        }
    }

    void updateCustomFee(List<FixedCustomFee> fixedCustomFees, RecordItem recordItem, long topicId) {
        var fixedFees = new ArrayList<FixedFee>();
        for (var fixedCustomFee : fixedCustomFees) {
            var collector = EntityId.of(fixedCustomFee.getFeeCollectorAccountId());
            var fixedFee = fixedCustomFee.getFixedFee();
            var tokenId = fixedFee.hasDenominatingTokenId() ? EntityId.of(fixedFee.getDenominatingTokenId()) : null;
            fixedFees.add(FixedFee.builder()
                    .amount(fixedFee.getAmount())
                    .collectorAccountId(collector)
                    .denominatingTokenId(tokenId)
                    .build());
            recordItem.addEntityId(collector);
            recordItem.addEntityId(tokenId);
        }

        var customFee = CustomFee.builder()
                .entityId(topicId)
                .fixedFees(fixedFees)
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
        entityListener.onCustomFee(customFee);
    }

    private void updateTopic(
            long consensusTimestamp, long topicId, ConsensusUpdateTopicTransactionBody transactionBody) {
        var adminKey =
                transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : null;
        // The fee exempt key list is not cleared in the database if it's an empty list, instead, importer would
        // serialize the protobuf message with an empty key list. The reader should understand that semantically
        // an empty list is the same as no fee exempt key list.
        var feeExemptKeyList = transactionBody.hasFeeExemptKeyList()
                ? transactionBody.getFeeExemptKeyList().toByteArray()
                : null;
        var feeScheduleKey = transactionBody.hasFeeScheduleKey()
                ? transactionBody.getFeeScheduleKey().toByteArray()
                : null;
        var submitKey =
                transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : null;
        var topic = Topic.builder()
                .adminKey(adminKey)
                .id(topicId)
                .feeExemptKeyList(feeExemptKeyList)
                .feeScheduleKey(feeScheduleKey)
                .submitKey(submitKey)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onTopic(topic);
    }
}
