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

import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Named
@AllArgsConstructor
public class ContractUpdateTransactionHandler implements TransactionHandler {

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractUpdateInstance().getContractID());
    }

    @Override
    public EntityId getProxyAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractUpdateInstance().getProxyAccountID());
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entities entity, RecordItem recordItem) {
        ContractUpdateTransactionBody txMessage = recordItem.getTransactionBody().getContractUpdateInstance();
        if (txMessage.hasExpirationTime()) {
            entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
        }

        if (txMessage.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
        }

        if (txMessage.hasAdminKey()) {
            entity.setKey(txMessage.getAdminKey().toByteArray());
        }

        switch (txMessage.getMemoFieldCase()) {
            case MEMOWRAPPER:
                entity.setMemo(txMessage.getMemoWrapper().getValue());
                break;
            case MEMO:
                if (txMessage.getMemo().length() > 0) {
                    entity.setMemo(txMessage.getMemo());
                }
                break;
            default:
                break;
        }
    }
}
