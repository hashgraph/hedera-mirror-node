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

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
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
                            () -> log.error(
                                    RECOVERABLE_ERROR + "Invalid autoRenewAccountId at {}",
                                    recordItem.getConsensusTimestamp()));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            Timestamp expirationTime = transactionBody.getExpirationTime();
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(expirationTime));
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        if (transactionBody.hasSubmitKey()) {
            entity.setSubmitKey(transactionBody.getSubmitKey().toByteArray());
        }

        entityListener.onEntity(entity);
    }
}
