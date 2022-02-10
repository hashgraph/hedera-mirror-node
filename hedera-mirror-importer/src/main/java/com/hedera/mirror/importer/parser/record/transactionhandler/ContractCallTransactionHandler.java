package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Named
class ContractCallTransactionHandler extends AbstractContractCallTransactionHandler {

    ContractCallTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                   EntityProperties entityProperties) {
        super(entityIdService, entityListener, entityProperties);
    }

    @Override
    public ContractResult getContractResult(Transaction transaction, RecordItem recordItem) {
        if (entityProperties.getPersist().isContracts()) {
            var transactionBody = recordItem.getTransactionBody().getContractCall();

            ContractResult contractResult = getBaseContractResult(transaction, recordItem,
                    recordItem.getRecord().getContractCallResult());
            contractResult.setAmount(transactionBody.getAmount());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getFunctionParameters()));
            contractResult.setGasLimit(transactionBody.getGas());

            return contractResult;
        }

        return null;
    }

    /**
     * First attempts to extract the contract ID from the receipt, which was populated in HAPI 0.23 for contract calls.
     * Otherwise, falls back to checking the transaction body which may contain an EVM address. In case of partial
     * mirror nodes, it's possible the database does not have the mapping for that EVM address in the body, hence the
     * need for prioritizing the receipt.
     *
     * @param recordItem to check
     * @return The contract ID associated with this contract call
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        ContractID contractIdBody = recordItem.getTransactionBody().getContractCall().getContractID();
        ContractID contractIdReceipt = recordItem.getRecord().getReceipt().getContractID();
        return entityIdService.lookup(contractIdReceipt, contractIdBody);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCALL;
    }

    // Will only be called for child created contract IDs.
    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        entityListener.onContract(contract);
    }
}
