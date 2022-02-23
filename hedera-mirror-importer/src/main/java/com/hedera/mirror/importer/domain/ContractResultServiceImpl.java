package com.hedera.mirror.importer.domain;

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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class ContractResultServiceImpl implements ContractResultService {

    protected final EntityProperties entityProperties;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;

    @Override
    public void process(RecordItem recordItem, EntityId entityId) {
        var transactionRecord = recordItem.getRecord();

        var functionResult = transactionRecord.hasContractCreateResult() ?
                transactionRecord.getContractCreateResult() : transactionRecord.getContractCallResult();

        // if transaction is neither a create/call and has no valid ContractFunctionResult then skip
        if (!recordItem.getTransactionBody().hasContractCreateInstance() &&
                !recordItem.getTransactionBody().hasContractCall() &&
                !isValidContractFunctionResult(functionResult)) {
            return;
        }

        // feature gate precompile scenarios for now. When complete feature gate all contractResults together
        if (isPrecompileCall(recordItem.getTransactionBody()) && !entityProperties.getPersist().isContractResults()) {
            return;
        }

        // contractResult
        ContractResult contractResult = getContractResult(recordItem, entityId, functionResult);
        updateContractFunctionWithTransactionInput(recordItem.getTransactionBody(), contractResult);
        entityListener.onContractResult(contractResult);

        // contractLog
        processContractLogs(functionResult, contractResult);

        // contractState
        processContractStateChanges(functionResult, contractResult);
    }

    private void updateContractFunctionWithTransactionInput(TransactionBody transactionBody,
                                                            ContractResult contractResult) {
        if (transactionBody.hasContractCall()) {
            var contractCallTransactionBody = transactionBody.getContractCall();
            contractResult.setAmount(contractCallTransactionBody.getAmount());
            contractResult.setFunctionParameters(
                    DomainUtils.toBytes(contractCallTransactionBody.getFunctionParameters()));
            contractResult.setGasLimit(contractCallTransactionBody.getGas());
        } else if (transactionBody.hasContractCreateInstance()) {
            var contractCreateTransactionBody = transactionBody.getContractCreateInstance();
            contractResult.setAmount(contractCreateTransactionBody.getInitialBalance());
            contractResult.setFunctionParameters(
                    DomainUtils.toBytes(contractCreateTransactionBody.getConstructorParameters()));
            contractResult.setGasLimit(contractCreateTransactionBody.getGas());
        }
    }

    private boolean isValidContractFunctionResult(ContractFunctionResult contractFunctionResult) {
        return contractFunctionResult != ContractFunctionResult.getDefaultInstance() &&
                contractFunctionResult.hasContractID();
    }

    private boolean isPrecompileCall(TransactionBody transactionBody) {
        return !transactionBody.hasContractCall() && !transactionBody.hasContractCreateInstance();
    }

    private boolean isContractCreateOrCall(TransactionBody transactionBody) {
        return transactionBody.hasContractCall() || transactionBody.hasContractCreateInstance();
    }

    private ContractResult getContractResult(RecordItem recordItem, EntityId contractEntityId,
                                             ContractFunctionResult functionResult) {
        ContractResult contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        contractResult.setPayerAccountId(recordItem.getPayerAccountId());

        if (functionResult != ContractFunctionResult.getDefaultInstance() && functionResult.hasContractID()) {
            // amount, gasLimit and functionParameters are missing from record proto and will be populated once added
            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setContractId(entityIdService.lookup(functionResult.getContractID()));
            contractResult.setCreatedContractIds(getCreatedContractIds(functionResult, recordItem, contractResult));
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());
        }

        // in case of failure for create/call pull entityId from transaction to avoid null case
        if (isContractCreateOrCall(recordItem.getTransactionBody()) && !recordItem.isSuccessful()) {
            contractResult.setContractId(contractEntityId);
        }

        return contractResult;
    }

    private void processContractLogs(ContractFunctionResult functionResult, ContractResult contractResult) {
        if (functionResult == null || contractResult == null) {
            return;
        }

        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);

            ContractLog contractLog = new ContractLog();
            contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
            contractLog.setContractId(entityIdService.lookup(contractLoginfo.getContractID()));
            contractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
            contractLog.setIndex(index);
            contractLog.setRootContractId(contractResult.getContractId());
            contractLog.setPayerAccountId(contractResult.getPayerAccountId());
            contractLog.setTopic0(Utility.getTopic(contractLoginfo, 0));
            contractLog.setTopic1(Utility.getTopic(contractLoginfo, 1));
            contractLog.setTopic2(Utility.getTopic(contractLoginfo, 2));
            contractLog.setTopic3(Utility.getTopic(contractLoginfo, 3));
            entityListener.onContractLog(contractLog);
        }
    }

    private void processContractStateChanges(ContractFunctionResult functionResult,
                                             ContractResult contractResult) {
        if (functionResult == null || contractResult == null) {
            return;
        }

        for (int stateIndex = 0; stateIndex < functionResult.getStateChangesCount(); ++stateIndex) {
            var contractStateChangeInfo = functionResult.getStateChanges(stateIndex);

            var contractId = entityIdService.lookup(contractStateChangeInfo.getContractID());
            for (var storageChange : contractStateChangeInfo.getStorageChangesList()) {
                ContractStateChange contractStateChange = new ContractStateChange();
                contractStateChange.setConsensusTimestamp(contractResult.getConsensusTimestamp());
                contractStateChange.setContractId(contractId);
                contractStateChange.setPayerAccountId(contractResult.getPayerAccountId());
                contractStateChange.setSlot(DomainUtils.toBytes(storageChange.getSlot()));
                contractStateChange.setValueRead(DomainUtils.toBytes(storageChange.getValueRead()));

                // If a value of zero is written the valueWritten will be present but the inner value will be
                // absent. If a value was read and not written this value will not be present.
                if (storageChange.hasValueWritten()) {
                    contractStateChange
                            .setValueWritten(DomainUtils.toBytes(storageChange.getValueWritten().getValue()));
                }

                entityListener.onContractStateChange(contractStateChange);
            }
        }
    }

    private List<Long> getCreatedContractIds(ContractFunctionResult functionResult, RecordItem recordItem,
                                             ContractResult contractResult) {
        List<Long> createdContractIds = new ArrayList<>();
        boolean persist = shouldPersistCreatedContractIDs(recordItem);
        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = entityIdService.lookup(createdContractId);
            createdContractIds.add(contractId.getId());
            if (persist && !EntityId.isEmpty(contractId) && !contractId.equals(
                    contractResult.getContractId())) {
                processCreatedContractEntity(recordItem, contractId);
            }
        }

        return createdContractIds;
    }

    private void processCreatedContractEntity(RecordItem recordItem, EntityId contractEntityId) {
        Contract contract = contractEntityId.toEntity();
        contract.setCreatedTimestamp(recordItem.getConsensusTimestamp());
        contract.setDeleted(false);
        contract.setTimestampLower(recordItem.getConsensusTimestamp());

        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            updateContractEntityOnCreate(contract, recordItem);
        }

        entityListener.onContract(contract);
    }

    private void updateContractEntityOnCreate(Contract contract, RecordItem recordItem) {
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
    }

    /**
     * Persist contract entities in createdContractIDs if it's prior to HAPI 0.23.0. After that the createdContractIDs
     * list is also externalized as contract create child records so we only need to persist the complete contract
     * entity from the child record.
     *
     * @param recordItem to check
     * @return Whether the createdContractIDs list should be persisted.
     */
    private boolean shouldPersistCreatedContractIDs(RecordItem recordItem) {
        return recordItem.isSuccessful() && entityProperties.getPersist().isContracts() &&
                recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0);
    }
}
