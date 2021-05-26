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
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Named
@AllArgsConstructor
public class ConsensusCreateTopicTransactionHandler implements TransactionHandler {

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getTopicID());
    }

    @Override
    public EntityId getAutoRenewAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getConsensusCreateTopic().getAutoRenewAccount());
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entity entity, RecordItem recordItem) {
        var createTopic = recordItem.getTransactionBody().getConsensusCreateTopic();
        if (createTopic.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(createTopic.getAutoRenewPeriod().getSeconds());
        }

        entity.setCreatedTimestamp(recordItem.getConsensusTimestamp());
        entity.setDeleted(false);

        // If either key is empty, they should end up as empty bytea in the DB to indicate that there is
        // explicitly no value, as opposed to null which has been used to indicate the value is unknown.
        var adminKey = createTopic.hasAdminKey() ? createTopic.getAdminKey().toByteArray() : new byte[0];
        var submitKey = createTopic.hasSubmitKey() ? createTopic.getSubmitKey().toByteArray() : new byte[0];
        entity.setMemo(createTopic.getMemo());
        entity.setKey(adminKey);
        entity.setSubmitKey(submitKey);
    }
}
