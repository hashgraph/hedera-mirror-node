/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.domain;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.migration.SidecarContractMigration;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
public class ContractResultServiceImpl implements ContractResultService {

    private final EntityProperties entityProperties;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final SidecarContractMigration sidecarContractMigration;
    private final TransactionHandlerFactory transactionHandlerFactory;

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;
    private static final long TX_CREATE_EXTRA = 32_000L;

    @Override
    public void process(@NonNull final RecordItem recordItem, final Transaction transaction) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        final var transactionBody = recordItem.getTransactionBody();
        final var transactionRecord = recordItem.getTransactionRecord();
        final var functionResult = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();

        final var sidecarProcessingResult = processSidecarRecords(recordItem);

        // handle non create/call transactions
        final var contractCallOrCreate = isContractCreateOrCall(transactionBody);
        if (!contractCallOrCreate && !isValidContractFunctionResult(functionResult)) {
            addDefaultEthereumTransactionContractResult(recordItem, transaction);
            // skip any other transaction which is neither a create/call and has no valid ContractFunctionResult
            return;
        }

        // contractResult
        final var transactionHandler = transactionHandlerFactory.get(TransactionType.of(transaction.getType()));

        // in pre-compile case transaction is not a contract type and entityId will be of a different type
        final var contractId = (contractCallOrCreate
                        ? Optional.ofNullable(transaction.getEntityId())
                        : entityIdService.lookup(functionResult.getContractID()))
                .orElse(EntityId.EMPTY);
        final var isRecoverableError = EntityId.isEmpty(contractId)
                && !contractCallOrCreate
                && !ContractID.getDefaultInstance().equals(functionResult.getContractID());
        if (isRecoverableError) {
            Utility.handleRecoverableError(
                    "Invalid contract id for contract result at {}", recordItem.getConsensusTimestamp());
        }

        recordItem.addEntityId(contractId);

        processContractResult(
                recordItem, contractId, functionResult, transaction, transactionHandler, sidecarProcessingResult);
    }

    private void addDefaultEthereumTransactionContractResult(
            final RecordItem recordItem, final Transaction transaction) {
        final var status = recordItem.getTransactionRecord().getReceipt().getStatus();
        if (recordItem.isSuccessful()
                || status == ResponseCodeEnum.DUPLICATE_TRANSACTION
                || status == ResponseCodeEnum.WRONG_NONCE
                || !recordItem.getTransactionBody().hasEthereumTransaction()) {
            // Don't add default contract result for the transaction if it's successful, or the result is
            // DUPLICATE_TRANSACTION, or the result is WRONG_NONCE, or it's not an ethereum transaction
            return;
        }

        final var ethereumTransaction = recordItem.getEthereumTransaction();
        final var functionParameters = ethereumTransaction.getCallData() != null
                ? ethereumTransaction.getCallData()
                : DomainUtils.EMPTY_BYTE_ARRAY;
        final var payerAccountId = transaction.getPayerAccountId();
        final var contractResult = ContractResult.builder()
                .callResult(DomainUtils.EMPTY_BYTE_ARRAY)
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .contractId(0)
                .functionParameters(functionParameters)
                .gasLimit(ethereumTransaction.getGasLimit())
                .gasUsed(0L)
                .payerAccountId(payerAccountId)
                .senderId(payerAccountId)
                .transactionHash(ethereumTransaction.getHash())
                .transactionIndex(transaction.getIndex())
                .transactionNonce(transaction.getNonce())
                .transactionResult(transaction.getResult())
                .build();
        entityListener.onContractResult(contractResult);
    }

    private boolean isValidContractFunctionResult(final ContractFunctionResult contractFunctionResult) {
        return !contractFunctionResult.equals(ContractFunctionResult.getDefaultInstance());
    }

    private boolean isContractCreateOrCall(final TransactionBody transactionBody) {
        return transactionBody.hasContractCall() || transactionBody.hasContractCreateInstance();
    }

    private void processContractAction(final ContractAction action, final int index, final RecordItem recordItem) {
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var contractAction = new com.hedera.mirror.common.domain.contract.ContractAction();

        try {
            switch (action.getCallerCase()) {
                case CALLING_CONTRACT -> {
                    contractAction.setCallerType(EntityType.CONTRACT);
                    contractAction.setCaller(EntityId.of(action.getCallingContract()));
                }
                case CALLING_ACCOUNT -> {
                    contractAction.setCallerType(EntityType.ACCOUNT);
                    contractAction.setCaller(EntityId.of(action.getCallingAccount()));
                }
                default -> Utility.handleRecoverableError(
                        "Invalid caller for contract action at {}: {}", consensusTimestamp, action.getCallerCase());
            }
        } catch (final InvalidEntityException e) {
            Utility.handleRecoverableError("Invalid caller for contract action at {}: {}", consensusTimestamp, action);
        }

        try {
            switch (action.getRecipientCase()) {
                case RECIPIENT_ACCOUNT -> contractAction.setRecipientAccount(EntityId.of(action.getRecipientAccount()));
                case RECIPIENT_CONTRACT -> contractAction.setRecipientContract(
                        EntityId.of(action.getRecipientContract()));
                case TARGETED_ADDRESS -> contractAction.setRecipientAddress(
                        action.getTargetedAddress().toByteArray());
                default -> {
                    // ContractCreate transaction has no recipient
                }
            }
        } catch (final InvalidEntityException e) {
            // In some cases, consensus nodes can send entity IDs with negative numbers.
            Utility.handleRecoverableError(
                    "Invalid recipient for contract action at {}: {}", consensusTimestamp, action);
        }

        switch (action.getResultDataCase()) {
            case ERROR -> contractAction.setResultData(DomainUtils.toBytes(action.getError()));
            case REVERT_REASON -> contractAction.setResultData(DomainUtils.toBytes(action.getRevertReason()));
            case OUTPUT -> contractAction.setResultData(DomainUtils.toBytes(action.getOutput()));
            default -> Utility.handleRecoverableError(
                    "Invalid result data for contract action at {}: {}",
                    consensusTimestamp,
                    action.getResultDataCase());
        }

        contractAction.setCallDepth(action.getCallDepth());
        contractAction.setCallOperationType(action.getCallOperationTypeValue());
        contractAction.setCallType(action.getCallTypeValue());
        contractAction.setConsensusTimestamp(consensusTimestamp);
        contractAction.setGas(action.getGas());
        contractAction.setGasUsed(action.getGasUsed());
        contractAction.setIndex(index);
        contractAction.setInput(DomainUtils.toBytes(action.getInput()));
        contractAction.setPayerAccountId(recordItem.getPayerAccountId());
        contractAction.setResultDataType(action.getResultDataCase().getNumber());
        contractAction.setValue(action.getValue());

        entityListener.onContractAction(contractAction);

        recordItem.addEntityId(contractAction.getCaller());
        recordItem.addEntityId(contractAction.getRecipientAccount());
        recordItem.addEntityId(contractAction.getRecipientContract());
    }

    private void processContractResult(
            final RecordItem recordItem,
            final EntityId contractEntityId,
            final ContractFunctionResult functionResult,
            final Transaction transaction,
            final TransactionHandler transactionHandler,
            final SidecarProcessingResult sidecarProcessingResult) {
        // create child contracts regardless of contractResults support
        final List<Long> contractIds = getCreatedContractIds(functionResult, recordItem, contractEntityId);
        processContractNonce(functionResult);

        if (!entityProperties.getPersist().isContractResults()) {
            return;
        }

        // Normalize the two distinct hashes into one 32 byte hash
        final var transactionHash = recordItem.getTransactionHash();
        final var payerAccountId = recordItem.getPayerAccountId();

        final var contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        contractResult.setContractId(contractEntityId.getId());
        contractResult.setPayerAccountId(payerAccountId);
        // senderId defaults to payerAccountId
        contractResult.setSenderId(payerAccountId);
        contractResult.setTransactionHash(transactionHash);
        contractResult.setTransactionIndex(transaction.getIndex());
        contractResult.setTransactionNonce(transaction.getNonce());
        contractResult.setTransactionResult(transaction.getResult());
        transactionHandler.updateContractResult(contractResult, recordItem);

        if (sidecarProcessingResult.failedInitByteCode() != null && contractResult.getFailedInitcode() == null) {
            contractResult.setFailedInitcode(DomainUtils.toBytes(sidecarProcessingResult.failedInitByteCode()));
        }

        if (isValidContractFunctionResult(functionResult)) {
            if (!isContractCreateOrCall(recordItem.getTransactionBody())) {
                // amount, gasLimit and functionParameters were missing from record proto prior to HAPI v0.25
                contractResult.setAmount(functionResult.getAmount());
                contractResult.setGasLimit(functionResult.getGas());
                contractResult.setFunctionParameters(DomainUtils.toBytes(functionResult.getFunctionParameters()));
            }

            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setCreatedContractIds(contractIds);
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());
            contractResult.setGasConsumed(sidecarProcessingResult.gasConsumed());

            if (functionResult.hasSenderId()) {
                final var senderId = EntityId.of(functionResult.getSenderId());
                contractResult.setSenderId(senderId);
                recordItem.addEntityId(senderId);
            }

            processContractLogs(functionResult, contractResult, recordItem);
        }
        recordItem.addContractTransaction(contractEntityId);
        // Add the transaction payer account id to enable proper lookup by transaction id
        recordItem.addContractTransaction(contractResult.getPayerAccountId());
        entityListener.onContractResult(contractResult);
    }

    private void processContractNonce(final ContractFunctionResult functionResult) {
        if (entityProperties.getPersist().isTrackNonce()) {
            functionResult.getContractNoncesList().forEach(nonceInfo -> {
                final var contractId = EntityId.of(nonceInfo.getContractId());
                final var entity = contractId.toEntity();
                entity.setEthereumNonce(nonceInfo.getNonce());
                entity.setTimestampRange(null); // Don't trigger a history row
                entity.setType(EntityType.CONTRACT);
                entityListener.onEntity(entity);
            });
        }
    }

    private void processContractLogs(
            final ContractFunctionResult functionResult,
            final ContractResult contractResult,
            final RecordItem recordItem) {
        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            final var contractLoginfo = functionResult.getLogInfo(index);
            final var contractLog = new ContractLog();
            final var contractId = EntityId.of(contractLoginfo.getContractID());
            final var rootContractId = EntityId.of(contractResult.getContractId());
            contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
            contractLog.setContractId(contractId);
            contractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
            contractLog.setIndex(index);
            contractLog.setRootContractId(rootContractId);
            contractLog.setPayerAccountId(contractResult.getPayerAccountId());
            contractLog.setTopic0(Utility.getTopic(contractLoginfo, 0));
            contractLog.setTopic1(Utility.getTopic(contractLoginfo, 1));
            contractLog.setTopic2(Utility.getTopic(contractLoginfo, 2));
            contractLog.setTopic3(Utility.getTopic(contractLoginfo, 3));
            contractLog.setTransactionHash(recordItem.getTransactionHash());
            contractLog.setTransactionIndex(recordItem.getTransactionIndex());
            entityListener.onContractLog(contractLog);

            recordItem.addContractTransaction(contractId);
            recordItem.addEntityId(contractId);
        }
    }

    private void processContractStateChange(
            final boolean migration, final RecordItem recordItem, final ContractStateChange stateChange) {
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var contractId = EntityId.of(stateChange.getContractId());
        final var payerAccountId = recordItem.getPayerAccountId();
        for (final var storageChange : stateChange.getStorageChangesList()) {
            final var contractStateChange = new com.hedera.mirror.common.domain.contract.ContractStateChange();
            contractStateChange.setConsensusTimestamp(consensusTimestamp);
            contractStateChange.setContractId(contractId);
            contractStateChange.setMigration(migration);
            contractStateChange.setPayerAccountId(payerAccountId);
            contractStateChange.setSlot(DomainUtils.toBytes(storageChange.getSlot()));
            contractStateChange.setValueRead(DomainUtils.toBytes(storageChange.getValueRead()));

            // If a value of zero is written the valueWritten will be present but the inner value will be
            // absent. If a value was read and not written this value will not be present.
            if (storageChange.hasValueWritten()) {
                contractStateChange.setValueWritten(
                        DomainUtils.toBytes(storageChange.getValueWritten().getValue()));
            }

            entityListener.onContractStateChange(contractStateChange);
            recordItem.addContractTransaction(contractId);
        }

        recordItem.addEntityId(contractId);
    }

    private List<Long> getCreatedContractIds(
            final ContractFunctionResult functionResult,
            final RecordItem recordItem,
            final EntityId parentEntityContractId) {
        final List<Long> createdContractIds = new ArrayList<>();
        final boolean persist = shouldPersistCreatedContractIDs(recordItem);
        for (final var createdContractId : functionResult.getCreatedContractIDsList()) {
            final var contractId = entityIdService.lookup(createdContractId).orElse(EntityId.EMPTY);
            if (!EntityId.isEmpty(contractId)) {
                createdContractIds.add(contractId.getId());
                // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
                if (persist && !contractId.equals(parentEntityContractId)) {
                    processCreatedContractEntity(recordItem, contractId);
                    recordItem.addEntityId(contractId);
                }
            }
        }

        return createdContractIds;
    }

    private void processCreatedContractEntity(final RecordItem recordItem, final EntityId contractEntityId) {
        final Entity entity = contractEntityId.toEntity();
        entity.setBalance(0L);
        entity.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        entity.setCreatedTimestamp(recordItem.getConsensusTimestamp());
        entity.setDeleted(false);
        entity.setMaxAutomaticTokenAssociations(0);
        entity.setTimestampLower(recordItem.getConsensusTimestamp());
        entity.setType(EntityType.CONTRACT);

        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            updateContractEntityOnCreate(entity, recordItem);
        }

        entityListener.onEntity(entity);
    }

    private SidecarProcessingResult processSidecarRecords(final RecordItem recordItem) {
        ByteString failedInitcode = null;
        final var sidecarRecords = recordItem.getSidecarRecords();
        if (sidecarRecords.isEmpty()) {
            return new SidecarProcessingResult(null, null);
        }

        final var contractBytecodes = new ArrayList<ContractBytecode>();
        int migrationCount = 0;
        final var stopwatch = Stopwatch.createStarted();

        long totalGasUsed = 0;
        ByteString contractDeployment = null;

        for (final var sidecarRecord : sidecarRecords) {
            final boolean migration = sidecarRecord.getMigration();
            if (sidecarRecord.hasStateChanges()) {
                final var stateChanges = sidecarRecord.getStateChanges();
                for (final var stateChange : stateChanges.getContractStateChangesList()) {
                    processContractStateChange(migration, recordItem, stateChange);
                }
            } else if (sidecarRecord.hasActions()) {
                final var actions = sidecarRecord.getActions();
                for (int actionIndex = 0; actionIndex < actions.getContractActionsCount(); actionIndex++) {
                    final var action = actions.getContractActions(actionIndex);
                    totalGasUsed = totalGasUsed + action.getGasUsed();
                    processContractAction(action, actionIndex, recordItem);
                }
            } else if (sidecarRecord.hasBytecode()) {
                if (migration) {
                    contractBytecodes.add(sidecarRecord.getBytecode());
                } else if (!recordItem.isSuccessful()) {
                    failedInitcode = sidecarRecord.getBytecode().getInitcode();
                }
            } else {
                contractDeployment = sidecarRecord.getBytecode().getInitcode();
            }
            if (migration) {
                ++migrationCount;
            }
        }

        sidecarContractMigration.migrate(contractBytecodes);
        if (migrationCount > 0) {
            log.info(
                    "{} Sidecar records processed with {} migrations in {}",
                    sidecarRecords.size(),
                    migrationCount,
                    stopwatch);
        }

        totalGasUsed = addIntrinsicGas(totalGasUsed, contractDeployment);

        return new SidecarProcessingResult(failedInitcode, totalGasUsed);
    }

    /**
     * Updates the contract entities in ContractCreateResult.CreatedContractIDs list. The method should only be called
     * for such contract entities in pre services 0.23 contract create transactions. Since services 0.23, the child
     * contract creation is externalized into its own synthesized contract create transaction and should be processed by
     * ContractCreateTransactionHandler.
     *
     * @param entity     The contract entity
     * @param recordItem The recordItem in which the contract is created
     */
    private void updateContractEntityOnCreate(final Entity entity, final RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        entity.setMemo(transactionBody.getMemo());

        final Contract contract = new Contract();
        contract.setId(entity.getId());

        // No need to check initcode and other newer fields since they weren't available in older HAPI versions
        if (transactionBody.hasFileID()) {
            contract.setFileId(EntityId.of(transactionBody.getFileID()));
        }

        entityListener.onContract(contract);
    }

    /**
     * Persist contract entities in createdContractIDs if it's prior to HAPI 0.23.0. After that the createdContractIDs
     * list is also externalized as contract create child records so we only need to persist the complete contract
     * entity from the child record.
     *
     * @param recordItem to check
     * @return Whether the createdContractIDs list should be persisted.
     */
    private boolean shouldPersistCreatedContractIDs(final RecordItem recordItem) {
        return recordItem.isSuccessful()
                && entityProperties.getPersist().isContracts()
                && recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0);
    }
    /**
     * Adds the intrinsic gas cost to the gas consumed by the EVM.
     * In case of contract deployment, the init code is used to
     * calculate the intrinsic gas cost. Otherwise, it's fixed at 21_000.
     *
     * @param gas The gas consumed by the EVM for the transaction
     * @param initByteCode The init code of the contract
     */
    private long addIntrinsicGas(final long gas, final ByteString initByteCode) {
        if (initByteCode == null) {
            return gas + TX_BASE_COST;
        }

        int zeros = 0;
        for (int i = 0; i < initByteCode.size(); i++) {
            if (initByteCode.byteAt(i) == 0) {
                ++zeros;
            }
        }
        final int nonZeros = initByteCode.size() - zeros;

        long costForByteCode = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

        return costForByteCode + gas + TX_CREATE_EXTRA;
    }

    /**
     * Record representing the result of processing sidecar records.
     *
     * @param failedInitByteCode The init code of the contract if the contract creation failed
     * @param gasConsumed The gas consumed by the EVM for the transaction.
     */
    private record SidecarProcessingResult(ByteString failedInitByteCode, Long gasConsumed) {}
}
