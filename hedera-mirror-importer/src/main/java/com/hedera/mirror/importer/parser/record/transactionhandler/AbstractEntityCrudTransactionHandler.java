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

import lombok.Getter;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;

abstract class AbstractEntityCrudTransactionHandler implements TransactionHandler {

    @Getter
    private final boolean createEntity;

    protected AbstractEntityCrudTransactionHandler() {
        this(false);
    }

    protected AbstractEntityCrudTransactionHandler(boolean createEntity) {
        this.createEntity = createEntity;
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entity entity, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();

        if (createEntity) {
            entity.setCreatedTimestamp(consensusTimestamp);
            entity.setDeleted(false);
        }

        entity.setModifiedTimestamp(consensusTimestamp);

        // stream contains account ID explicitly set to the default '0.0.0'
        EntityId autoRenewAccountId = getAutoRenewAccount(recordItem);
        if (!EntityId.isEmpty(autoRenewAccountId)) {
            entity.setAutoRenewAccountId(autoRenewAccountId);
        }

        EntityId proxyAccountId = getProxyAccount(recordItem);
        if (!EntityId.isEmpty(proxyAccountId)) {
            entity.setProxyAccountId(proxyAccountId);
        }

        doUpdateEntity(entity, recordItem);
    }

    protected abstract void doUpdateEntity(Entity entity, RecordItem recordItem);

    protected EntityId getAutoRenewAccount(RecordItem recordItem) {
        return null;
    }

    protected EntityId getProxyAccount(RecordItem recordItem) {
        return  null;
    }
}
