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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import javax.inject.Named;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Named
class ContractCallTransactionHandler extends AbstractContractCallTransactionHandler {

    ContractCallTransactionHandler(EntityListener entityListener, EntityProperties entityProperties) {
        super(entityListener, entityProperties);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getContractCall().getContractID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCALL;
    }

    /*
     * Insert contract results even for failed transactions since they could fail during execution, and we want to
     * know the gas used and call result regardless.
     */
    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionRecord = recordItem.getRecord();

        if (entityProperties.getPersist().isContracts()) {
            var transactionBody = recordItem.getTransactionBody().getContractCall();
            var functionResult = transactionRecord.hasContractCallResult() ?
                    transactionRecord.getContractCallResult() : ContractFunctionResult.getDefaultInstance();

            // The functionResult.contractID can sometimes be empty even if successful, so use Transaction.entityId
            ContractResult contractResult = new ContractResult();
            contractResult.setAmount(transactionBody.getAmount());
            contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
            contractResult.setContractId(transaction.getEntityId());
            contractResult.setPayerAccountId(transaction.getPayerAccountId());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getFunctionParameters()));
            contractResult.setGasLimit(transactionBody.getGas());

            onContractResult(recordItem, contractResult, functionResult);
        }
    }

    // Will only be called for child created contract IDs.
    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        entityListener.onContract(contract);
    }
}
