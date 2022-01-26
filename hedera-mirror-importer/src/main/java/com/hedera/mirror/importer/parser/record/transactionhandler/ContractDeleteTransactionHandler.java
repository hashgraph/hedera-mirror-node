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
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class ContractDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler<Contract> {

    private final EntityIdService entityIdService;

    ContractDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityListener, TransactionType.CONTRACTDELETEINSTANCE);
        this.entityIdService = entityIdService;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return entityIdService.lookup(recordItem.getTransactionBody().getContractDeleteInstance().getContractID());
    }

    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractDeleteInstance();
        EntityId obtainerId = null;

        if (transactionBody.hasTransferAccountID()) {
            obtainerId = EntityId.of(transactionBody.getTransferAccountID());
        } else if (transactionBody.hasTransferContractID()) {
            obtainerId = entityIdService.lookup(transactionBody.getTransferContractID());
        }

        contract.setObtainerId(obtainerId);
        entityListener.onContract(contract);
    }
}
