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

import com.hederahashgraph.api.proto.java.ContractID;
import javax.inject.Named;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class ContractDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ContractDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                     RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CONTRACTDELETEINSTANCE);
    }

    /**
     * First attempts to extract the contract ID from the receipt, which was populated in HAPI 0.23 for contract
     * deletes. Otherwise, falls back to checking the transaction body which may contain an EVM address. In case of
     * partial mirror nodes, it's possible the database does not have the mapping for that EVM address in the body,
     * hence the need for prioritizing the receipt.
     *
     * @param recordItem to check
     * @return The contract ID associated with this contract delete
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        ContractID contractIdBody = recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
        ContractID contractIdReceipt = recordItem.getTransactionRecord().getReceipt().getContractID();
        return entityIdService.lookup(contractIdReceipt, contractIdBody);
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractDeleteInstance();
        EntityId obtainerId = null;

        if (transactionBody.hasTransferAccountID()) {
            obtainerId = EntityId.of(transactionBody.getTransferAccountID());
        } else if (transactionBody.hasTransferContractID()) {
            obtainerId = entityIdService.lookup(transactionBody.getTransferContractID());
        }

        entity.setObtainerId(obtainerId);
        entity.setPermanentRemoval(transactionBody.getPermanentRemoval());
        entityListener.onEntity(entity);
    }
}
