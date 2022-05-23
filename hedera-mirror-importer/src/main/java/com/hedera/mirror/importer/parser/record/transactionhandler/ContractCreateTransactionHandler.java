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

import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import javax.inject.Named;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

@Named
class ContractCreateTransactionHandler extends AbstractEntityCrudTransactionHandler<Contract> {

    private final EntityProperties entityProperties;
    private final EthereumTransactionParser ethereumTransactionParser;

    ContractCreateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                     EntityProperties entityProperties,
                                     EthereumTransactionParser ethereumTransactionParser,
                                     RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CONTRACTCREATEINSTANCE);
        this.entityProperties = entityProperties;
        this.ethereumTransactionParser = ethereumTransactionParser;
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
    public void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        transaction.setInitialBalance(transactionBody.getInitialBalance());
    }

    @Override
    protected void doUpdateEntity(Contract contract, RecordItem recordItem) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        var contractCreateResult = recordItem.getRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewAccountId()) {
            getAccountId(transactionBody.getAutoRenewAccountId())
                    .map(EntityId::getId)
                    .ifPresent(contract::setAutoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            contract.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            contract.setKey(transactionBody.getAdminKey().toByteArray());
        }

        switch (transactionBody.getInitcodeSourceCase()) {
            case FILEID:
                contract.setFileId(EntityId.of(transactionBody.getFileID()));
                break;
            case INITCODE:
                contract.setInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
                break;
            default:
                break;
        }

        contract.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations());

        if (transactionBody.hasProxyAccountID()) {
            contract.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        if (contractCreateResult.hasEvmAddress()) {
            contract.setEvmAddress(DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()));
        }

        contract.setMemo(transactionBody.getMemo());

        // for child transactions initCode and FileID are located in parent ContractCreate/EthereumTransaction types
        updateChildFromParent(contract, recordItem);

        updateContractStakingInfo(contract, transactionBody);

        entityListener.onContract(contract);
    }

    private void updateContractStakingInfo(Contract contract, ContractCreateTransactionBody transactionBody) {
        contract.setDeclineReward(transactionBody.getDeclineReward());
        if (transactionBody.getStakedIdCase() == ContractCreateTransactionBody.StakedIdCase.STAKED_ACCOUNT_ID) {
            EntityId accountId = EntityId.of(transactionBody.getStakedAccountId());
            contract.updateAccountId(accountId);
        } else {
            contract.setStakedNodeId(transactionBody.getStakedNodeId());
        }
        contract.startNewStakingPeriod();
    }

    @Override
    public void updateContractResult(ContractResult contractResult, RecordItem recordItem) {
        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
            contractResult.setAmount(transactionBody.getInitialBalance());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getConstructorParameters()));
            contractResult.setGasLimit(transactionBody.getGas());
        }
    }

    private void updateChildFromParent(Contract contract, RecordItem recordItem) {
        if (!recordItem.isChild() || recordItem.getParent() == null) {
            return;
        }

        // parents may be ContractCreate or EthereumTransaction
        var parentRecordItem = recordItem.getParent();
        switch (TransactionType.of(parentRecordItem.getTransactionType())) {
            case CONTRACTCREATEINSTANCE:
                updateChildFromContractCreateParent(contract, parentRecordItem);
                break;
            case ETHEREUMTRANSACTION:
                updateChildFromEthereumTransactionParent(contract, parentRecordItem);
                break;
            default:
                break;
        }
    }

    private void updateChildFromContractCreateParent(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody()
                .getContractCreateInstance();
        switch (transactionBody.getInitcodeSourceCase()) {
            case FILEID:
                if (contract.getFileId() == null) {
                    contract.setFileId(EntityId.of(transactionBody.getFileID()));
                }
                break;
            case INITCODE:
                if (contract.getInitcode() == null) {
                    contract.setInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
                }
                break;
            default:
                // should we throw in this case?
                break;
        }
    }

    private void updateChildFromEthereumTransactionParent(Contract contract, RecordItem recordItem) {
        var body = recordItem.getTransactionBody().getEthereumTransaction();

        // use callData FileID if present
        if (body.hasCallData()) {
            contract.setFileId(EntityId.of(body.getCallData()));
            return;
        }

        var ethereumDataBytes = DomainUtils.toBytes(body.getEthereumData());
        var ethereumTransaction = ethereumTransactionParser.decode(ethereumDataBytes);

        if (contract.getInitcode() == null) {
            contract.setInitcode(ethereumTransaction.getCallData());
        }
    }
}
