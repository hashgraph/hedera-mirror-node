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

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class ContractUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler<Contract> {

    ContractUpdateTransactionHandler(EntityListener entityListener) {
        super(entityListener, TransactionType.CONTRACTUPDATEINSTANCE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractUpdateInstance().getContractID());
    }

    // We explicitly ignore the updated fileID field since hedera nodes do not allow changing the bytecode after create
    @SuppressWarnings("java:S1874")
    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractUpdateInstance();

        if (transactionBody.hasExpirationTime()) {
            contract.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpirationTime()));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            contract.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            contract.setKey(transactionBody.getAdminKey().toByteArray());
        }

        switch (transactionBody.getMemoFieldCase()) {
            case MEMOWRAPPER:
                contract.setMemo(transactionBody.getMemoWrapper().getValue());
                break;
            case MEMO:
                if (transactionBody.getMemo().length() > 0) {
                    contract.setMemo(transactionBody.getMemo());
                }
                break;
            default:
                break;
        }

        if (transactionBody.hasProxyAccountID()) {
            contract.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        entityListener.onContract(contract);
    }
}
