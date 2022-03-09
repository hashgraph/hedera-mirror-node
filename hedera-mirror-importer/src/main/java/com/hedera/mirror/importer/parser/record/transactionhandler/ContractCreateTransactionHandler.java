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

import javax.inject.Named;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Named
class ContractCreateTransactionHandler extends AbstractContractCallTransactionHandler {

    ContractCreateTransactionHandler(ContractResultService contractResultService, EntityIdService entityIdService,
                                     EntityListener entityListener, EntityProperties entityProperties) {
        super(contractResultService, entityIdService, entityListener, entityProperties);
    }

    @Override
    public ContractResult getContractResult(Transaction transaction, RecordItem recordItem) {
        if (entityProperties.getPersist().isContracts()) {
            var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

            ContractResult contractResult = getBaseContractResult(transaction, recordItem);
            
            // set input values from transactionBody params
            contractResult.setAmount(transactionBody.getInitialBalance());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getConstructorParameters()));
            contractResult.setGasLimit(transactionBody.getGas());

            return contractResult;
        }

        return null;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return entityIdService.lookup(recordItem.getRecord().getReceipt().getContractID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCREATEINSTANCE;
    }

    /*
     * Insert contract results even for failed transactions since they could fail during execution, and we want to
     * know how much gas was used and the call result regardless.
     */
    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        EntityId entityId = transaction.getEntityId();
        transaction.setInitialBalance(transactionBody.getInitialBalance());

        if (entityProperties.getPersist().isContracts() && recordItem.isSuccessful() && !EntityId.isEmpty(entityId)) {
            doUpdateEntity(getContract(entityId, consensusTimestamp), recordItem);
        }
    }

    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        var contractCreateResult = recordItem.getRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewPeriod()) {
            contract.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            contract.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            contract.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        if (transactionBody.hasFileID()) {
            contract.setFileId(EntityId.of(transactionBody.getFileID()));
        }

        if (contractCreateResult.hasEvmAddress()) {
            contract.setEvmAddress(DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()));
        }

        contract.setMemo(transactionBody.getMemo());
        entityListener.onContract(contract);
    }
}
