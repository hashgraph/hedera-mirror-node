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

import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Named
public class CryptoUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    public CryptoUpdateTransactionHandler() {
        super(EntityOperationEnum.UPDATE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        CryptoUpdateTransactionBody txMessage = recordItem.getTransactionBody().getCryptoUpdateAccount();
        if (txMessage.hasExpirationTime()) {
            entity.setExpirationTimestamp(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
        }

        if (txMessage.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
        }

        if (txMessage.hasKey()) {
            entity.setKey(txMessage.getKey().toByteArray());
        }

        if (txMessage.hasMaxAutomaticTokenAssociations()) {
            long value = Integer.toUnsignedLong(txMessage.getMaxAutomaticTokenAssociations().getValue());
            entity.setMaxAutomaticTokenAssociations(value);
        }

        if (txMessage.hasMemo()) {
            entity.setMemo(txMessage.getMemo().getValue());
        }
    }

    @Override
    protected EntityId getProxyAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getCryptoUpdateAccount().getProxyAccountID());
    }
}
