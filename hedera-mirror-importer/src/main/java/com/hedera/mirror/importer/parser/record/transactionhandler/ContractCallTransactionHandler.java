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

import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@Named
class ContractCallTransactionHandler extends AbstractContractCallTransactionHandler {

    private final EntityProperties entityProperties;

    ContractCallTransactionHandler(EntityListener entityListener, EntityProperties entityProperties) {
        super(entityListener);
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractCall().getContractID());
    }

    @Override
    public TransactionTypeEnum getType() {
        return TransactionTypeEnum.CONTRACTCALL;
    }

    /*
     * Insert contract results even for failed transactions since they could fail during execution, and we want to
     * how the gas used and call result regardless.
     */
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionRecord = recordItem.getRecord();

        if (entityProperties.getPersist().isContracts() && transactionRecord.hasContractCallResult()) {
            var transactionBody = recordItem.getTransactionBody().getContractCall();
            var functionResult = transactionRecord.getContractCallResult();

            // The functionResult.contractID can sometimes be empty even if successful, so use Transaction.entityId
            ContractResult contractResult = new ContractResult();
            contractResult.setAmount(transactionBody.getAmount());
            contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            contractResult.setContractId(transaction.getEntityId());
            contractResult.setFunctionParameters(Utility.toBytes(transactionBody.getFunctionParameters()));
            contractResult.setGasLimit(transactionBody.getGas());

            onContractResult(recordItem, contractResult, functionResult);
        }
    }

    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        entityListener.onContract(contract);
    }
}
