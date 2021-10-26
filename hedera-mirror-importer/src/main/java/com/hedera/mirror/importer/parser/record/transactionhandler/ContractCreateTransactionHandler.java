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

import java.util.function.Supplier;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Cloner;
import com.hedera.mirror.importer.util.Utility;

@Named
class ContractCreateTransactionHandler extends AbstractContractCallTransactionHandler {

    private final Cloner cloner;
    private final EntityProperties entityProperties;

    ContractCreateTransactionHandler(Cloner cloner, EntityListener entityListener, EntityProperties entityProperties) {
        super(entityListener);
        this.cloner = cloner;
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getContractID());
    }

    @Override
    public TransactionTypeEnum getType() {
        return TransactionTypeEnum.CONTRACTCREATEINSTANCE;
    }

    /*
     * Insert contract results even for failed transactions since they could fail during execution, and we want to
     * know how much gas was used and the call result regardless.
     */
    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var transactionRecord = recordItem.getRecord();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        EntityId entityId = transaction.getEntityId();
        transaction.setInitialBalance(transactionBody.getInitialBalance());
        Supplier<Contract> inheritedContract = Contract::new;

        if (!EntityId.isEmpty(entityId) && recordItem.isSuccessful()) {
            final Contract contract = entityId.toEntity();
            contract.setCreatedTimestamp(consensusTimestamp);
            contract.setDeleted(false);
            contract.setModifiedTimestamp(consensusTimestamp);
            doUpdateEntity(contract, recordItem);
            inheritedContract = () -> cloner.deepClone(contract);
        }

        if (entityProperties.getPersist().isContracts() && transactionRecord.hasContractCreateResult()) {
            var functionResult = transactionRecord.getContractCreateResult();

            ContractResult contractResult = new ContractResult();
            contractResult.setAmount(transactionBody.getInitialBalance());
            contractResult.setConsensusTimestamp(consensusTimestamp);
            contractResult.setContractId(EntityId.of(transactionRecord.getReceipt().getContractID()));
            contractResult.setFunctionParameters(Utility.toBytes(transactionBody.getConstructorParameters()));
            contractResult.setGasLimit(transactionBody.getGas());

            onContractResult(recordItem, inheritedContract, contractResult, functionResult);
        }
    }

    @Override
    protected void doUpdateEntity(Contract contract, final RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewPeriod()) {
            contract.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            contract.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            EntityId proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            contract.setProxyAccountId(proxyAccountId);
            entityListener.onEntityId(proxyAccountId);
        }

        if (transactionBody.hasFileID()) {
            contract.setFileId(EntityId.of(transactionBody.getFileID()));
        }

        contract.setMemo(transactionBody.getMemo());
        entityListener.onContract(contract);
    }
}
