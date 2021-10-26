package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.inject.Named;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class ConsensusCreateTopicTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    private static final byte[] EMPTY = new byte[0];

    ConsensusCreateTopicTransactionHandler(EntityListener entityListener) {
        super(entityListener, TransactionTypeEnum.CONSENSUSCREATETOPIC);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getTopicID());
    }

    @Override
    protected void doUpdateEntity(final Entity entity, final RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getConsensusCreateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            EntityId autoRenewAccountId = EntityId.of(transactionBody.getAutoRenewAccount());
            entity.setAutoRenewAccountId(autoRenewAccountId);
            entityListener.onEntityId(autoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        // If either key is empty, they should end up as empty bytea in the DB to indicate that there is
        // explicitly no value, as opposed to null which has been used to indicate the value is unknown.
        var adminKey = transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : EMPTY;
        var submitKey = transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : EMPTY;
        entity.setMemo(transactionBody.getMemo());
        entity.setKey(adminKey);
        entity.setSubmitKey(submitKey);
        entityListener.onEntity(entity);
    }
}
