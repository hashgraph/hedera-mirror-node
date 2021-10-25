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
import com.hedera.mirror.importer.util.Utility;

@Named
class CryptoUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    CryptoUpdateTransactionHandler(EntityListener entityListener) {
        super(entityListener, TransactionTypeEnum.CRYPTOUPDATEACCOUNT);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
    }

    @Override
    @SuppressWarnings("java:S1874")
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            entity.setExpirationTimestamp(Utility.timestampInNanosMax(transactionBody.getExpirationTime()));
        }

        if (transactionBody.hasKey()) {
            entity.setKey(transactionBody.getKey().toByteArray());
        }

        if (transactionBody.hasMaxAutomaticTokenAssociations()) {
            entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations().getValue());
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        if (transactionBody.hasProxyAccountID()) {
            EntityId proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountId);
            entityListener.onEntityId(proxyAccountId);
        }

        if (transactionBody.hasReceiverSigRequiredWrapper()) {
            entity.setReceiverSigRequired(transactionBody.getReceiverSigRequiredWrapper().getValue());
        } else if (transactionBody.getReceiverSigRequired()) {
            // support old transactions
            entity.setReceiverSigRequired(transactionBody.getReceiverSigRequired());
        }

        entityListener.onEntity(entity);
    }
}
