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

import javax.inject.Named;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class ConsensusCreateTopicTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private static final byte[] EMPTY = new byte[0];

    ConsensusCreateTopicTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                           RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CONSENSUSCREATETOPIC);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getTopicID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getConsensusCreateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            entity.setAutoRenewAccountId(EntityId.of(transactionBody.getAutoRenewAccount()).getId());
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
