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

import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Named
public class ContractCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    public ContractCreateTransactionHandler() {
        super(EntityOperationEnum.CREATE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getContractID());
    }

    @Override
    public EntityId getProxyAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractCreateInstance().getProxyAccountID());
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setInitialBalance(recordItem.getTransactionBody().getContractCreateInstance().getInitialBalance());
    }

    @Override
    protected void doUpdateEntity(final Entity entity, final RecordItem recordItem) {
        ContractCreateTransactionBody txMessage = recordItem.getTransactionBody().getContractCreateInstance();
        if (txMessage.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
        }

        if (txMessage.hasAdminKey()) {
            entity.setKey(txMessage.getAdminKey().toByteArray());
        }

        entity.setMemo(txMessage.getMemo());
    }
}
