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

import static com.hederahashgraph.api.proto.java.ContractCreateTransactionBody.InitcodeSourceCase.INITCODE;

import javax.inject.Named;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@Named
class ContractCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;

    ContractCreateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                     EntityProperties entityProperties,
                                     RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CONTRACTCREATEINSTANCE);
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return entityIdService.lookup(recordItem.getTransactionRecord().getReceipt().getContractID());
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
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewAccountId()) {
            getAccountId(transactionBody.getAutoRenewAccountId())
                    .map(EntityId::getId)
                    .ifPresent(entity::setAutoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (contractCreateResult.hasEvmAddress()) {
            entity.setEvmAddress(DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()));
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        entity.setBalance(0L);
        entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations());
        entity.setMemo(transactionBody.getMemo());
        updateStakingInfo(recordItem, entity);
        createContract(recordItem, entity);
        entityListener.onEntity(entity);
    }

    private void createContract(RecordItem recordItem, Entity entity) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        Contract contract = new Contract();
        contract.setId(entity.getId());

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

        var contractId = recordItem.getTransactionRecord().getReceipt().getContractID();
        var sidecarRecords = recordItem.getSidecarRecords();

        for (var sidecar : sidecarRecords) {
            if (sidecar.hasBytecode() && !sidecar.getMigration()) {
                var bytecode = sidecar.getBytecode();
                if (contractId.equals(bytecode.getContractId())) {
                    if (contract.getInitcode() == null) {
                        contract.setInitcode(DomainUtils.toBytes(bytecode.getInitcode()));
                    }

                    contract.setRuntimeBytecode(DomainUtils.toBytes(bytecode.getRuntimeBytecode()));
                    break;
                }
            }
        }

        // for child transactions FileID is located in parent ContractCreate/EthereumTransaction types
        // and initcode is located in the sidecar
        updateChildFromParent(contract, recordItem);
        entityListener.onContract(contract);
    }

    private void updateStakingInfo(RecordItem recordItem, Entity contract) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        contract.setDeclineReward(transactionBody.getDeclineReward());

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET:
                return;
            case STAKED_NODE_ID:
                contract.setStakedNodeId(transactionBody.getStakedNodeId());
                break;
            case STAKED_ACCOUNT_ID:
                EntityId accountId = EntityId.of(transactionBody.getStakedAccountId());
                contract.setStakedAccountId(accountId.getId());
                break;
        }

        contract.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
    }

    @Override
    public void updateContractResult(ContractResult contractResult, RecordItem recordItem) {
        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
            contractResult.setAmount(transactionBody.getInitialBalance());
            contractResult.setFunctionParameters(DomainUtils.toBytes(transactionBody.getConstructorParameters()));
            contractResult.setGasLimit(transactionBody.getGas());
            if (!recordItem.isSuccessful() && transactionBody.getInitcodeSourceCase() == INITCODE) {
                contractResult.setFailedInitcode(DomainUtils.toBytes(transactionBody.getInitcode()));
            }
        }
    }

    private void updateChildFromParent(Contract contract, RecordItem recordItem) {
        if (!recordItem.isChild() || recordItem.getParent() == null) {
            return;
        }

        // Parents may be either ContractCreate or EthereumTransaction
        var parentRecordItem = recordItem.getParent();
        var type = TransactionType.of(parentRecordItem.getTransactionType());

        switch (type) {
            case CONTRACTCREATEINSTANCE -> updateChildFromContractCreateParent(contract, parentRecordItem);
            case ETHEREUMTRANSACTION -> updateChildFromEthereumTransactionParent(contract, parentRecordItem);
            default -> {
                //no-op
            }
        }
    }

    private void updateChildFromContractCreateParent(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

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
        if (body.hasCallData() && contract.getFileId() == null) {
            contract.setFileId(EntityId.of(body.getCallData()));
            return;
        }

        if (contract.getInitcode() == null && recordItem.getEthereumTransaction() != null) {
            contract.setInitcode(recordItem.getEthereumTransaction().getCallData());
        }
    }
}
