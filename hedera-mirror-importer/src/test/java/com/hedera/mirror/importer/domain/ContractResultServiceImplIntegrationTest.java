package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static com.hedera.services.stream.proto.ContractAction.CallerCase.CALLING_CONTRACT;
import static com.hedera.services.stream.proto.ContractAction.RecipientCase.RECIPIENT_NOT_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Version;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.repository.ContractStateRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractResultServiceImplIntegrationTest extends IntegrationTest {

    private final ContractRepository contractRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractResultService contractResultService;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate transactionTemplate;

    private Transaction transaction;

    @Test
    void processContractCall() {
        RecordItem recordItem = recordItemBuilder.contractCall().build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @SuppressWarnings("deprecation")
    @Test
    void processContractCreate() {
        RecordItem recordItem = recordItemBuilder.contractCreate().build();
        ContractFunctionResult contractFunctionResult = recordItem.getTransactionRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var entityId = EntityId.of(contractFunctionResult.getCreatedContractIDs(0)).getId();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(EntityId.of(transactionBody.getFileID()), Contract::getFileId)
                .returns(entityId, Contract::getId);
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(0L, Entity::getBalance)
                .returns(recordItem.getConsensusTimestamp(), Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(entityId, Entity::getId)
                .returns(transactionBody.getAdminKey().toByteArray(), Entity::getKey)
                .returns(EntityId.of(transactionBody.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(0, Entity::getMaxAutomaticTokenAssociations)
                .returns(transactionBody.getMemo(), Entity::getMemo);
    }

    @Test
    void processContractCreateNoChildren() {
        var recordItem = recordItemBuilder.contractCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 24, 0)))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processEthereumTransaction() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        var recordItem = recordItemBuilder.ethereumTransaction()
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .build();

        process(recordItem);

        assertContractResult(recordItem, true);
        assertContractLogs(recordItem, true);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractActionInvalidCaller() {
        var recordItem = recordItemBuilder.contractCall()
                .sidecarRecords(s -> s.get(1).getActionsBuilder().getContractActionsBuilder(0).clearCaller())
                .build();

        assertThatThrownBy(() -> process(recordItem)).isInstanceOf(InvalidDatasetException.class)
                .hasMessageContaining("Invalid caller");
    }

    @Test
    void processContractActionInvalidResultData() {
        var recordItem = recordItemBuilder.contractCall()
                .sidecarRecords(s -> s.get(1).getActionsBuilder().getContractActionsBuilder(0).clearResultData())
                .build();

        assertThatThrownBy(() -> process(recordItem)).isInstanceOf(InvalidDatasetException.class)
                .hasMessageContaining("Invalid result data");
    }

    @Test
    void processPrecompile() {
        RecordItem recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON)
                .record(x -> x.setContractCallResult(recordItemBuilder.contractFunctionResult()))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractStateRepository.count()).isZero();
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void processContractCallDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder.contractCall().record(x -> x.clearContractCallResult()).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractCreateDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder.contractCreate().record(x -> x.clearContractCreateResult()).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processNoContractLogs() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(x -> x.getContractCreateResultBuilder().clearLogInfo())
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractLogRepository.count()).isZero();
    }

    @Test
    void processNoSidecars() {
        RecordItem recordItem = recordItemBuilder.contractCreate().sidecarRecords(List::clear).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractStateRepository.count()).isZero();
    }

    @Test
    void processContractCallFailure() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(TransactionRecord.Builder::clearContractCallResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractCreateFailure() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .transactionBody(t -> t.setInitcode(ByteString.copyFrom(new byte[] {9, 8, 7})))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processSidecarContractCreateFailure() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrateContractSidecar(boolean migration) {
        // given
        var entity1 = domainBuilder.entity().persist();
        var entity2 = domainBuilder.entity().persist();
        var entity3 = domainBuilder.entity().persist();
        domainBuilder.contract().customize(c -> c.id(entity1.getId())).persist();
        domainBuilder.contract().customize(c -> c.id(entity2.getId()).runtimeBytecode(null)).persist();
        domainBuilder.contract().customize(c -> c.id(entity3.getId())).persist();

        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var stateChangeRecord1 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1001L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {1, 1})))))
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1002L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {2, 2}))))))
                .build();
        var stateChangeRecord2 = TransactionSidecarRecord.newBuilder()
                .setMigration(false)
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1003L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {3, 3})))))
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1004L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {4, 4}))))))
                .build();
        var bytecodeRecord1 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(entity1.getId()))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {1})))
                .build();
        var bytecodeRecord2 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(entity2.getId()))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {2})))
                .build();
        var bytecodeRecord3 = TransactionSidecarRecord.newBuilder()
                .setMigration(false)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(entity3.getId()))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {3})))
                .build();

        var contractActionRecord = TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addContractActions(contractAction(3001L,
                                CallOperationType.OP_CALL,
                                ContractActionType.CALL,
                                3002L))
                        .addContractActions(contractAction(3003L,
                                CallOperationType.OP_CREATE,
                                ContractActionType.CREATE,
                                3004L))
                        .addContractActions(contractAction(3004L,
                                CallOperationType.OP_DELEGATECALL,
                                ContractActionType.CALL,
                                3005L)))
                .build();


        recordItem.setSidecarRecords(List.of(stateChangeRecord1, stateChangeRecord2, bytecodeRecord1, bytecodeRecord2,
                bytecodeRecord3, contractActionRecord));

        // when
        process(recordItem);

        // then
        assertContractStateChanges(recordItem);
        assertContractRuntimeBytecode(recordItem);
        assertEntityContractType(recordItem);
    }

    private void assertEntityContractType(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords()
                .stream()
                .filter(TransactionSidecarRecord::getMigration)
                .filter(TransactionSidecarRecord::hasBytecode)
                .map(TransactionSidecarRecord::getBytecode)
                .map(b -> EntityId.of(b.getContractId()).getId())
                .toList();
        assertThat(entityRepository.findAll())
                .filteredOn(e -> e.getType() == CONTRACT)
                .map(Entity::getId)
                .containsAll(expected);
    }

    @SuppressWarnings("deprecation")
    private void assertContractResult(RecordItem recordItem) {
        assertContractResult(recordItem, false);
    }

    private void assertContractResult(RecordItem recordItem, boolean ethereum) {
        var functionResult = getFunctionResult(recordItem);
        var createdIds = functionResult.getCreatedContractIDsList()
                .stream()
                .map(x -> EntityId.of(x).getId())
                .collect(Collectors.toList());
        var failedInitcode = getFailedInitcode(recordItem);
        var hash = ethereum ? recordItem.getEthereumTransaction().getHash() :
                Arrays.copyOfRange(transaction.getTransactionHash(), 0, 32);

        assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), ContractResult::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), ContractResult::getPayerAccountId)
                .returns(toBytes(functionResult.getBloom()), ContractResult::getBloom)
                .returns(toBytes(functionResult.getContractCallResult()), ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(parseContractResultStrings(functionResult.getErrorMessage()), ContractResult::getErrorMessage)
                .returns(parseContractResultLongs(functionResult.getGasUsed()), ContractResult::getGasUsed)
                .returns(toBytes(failedInitcode), ContractResult::getFailedInitcode)
                .returns(transaction.getIndex(), ContractResult::getTransactionIndex)
                .returns(transaction.getResult(), ContractResult::getTransactionResult)
                .extracting(ContractResult::getTransactionHash, InstanceOfAssertFactories.BYTE_ARRAY)
                .hasSize(32)
                .isEqualTo(hash);
    }

    private void assertContractActions(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords()
                .stream()
                .map(TransactionSidecarRecord::getActions)
                .map(actions -> {
                    var actionsMap = new HashMap<ContractAction.Id, com.hedera.services.stream.proto.ContractAction>();
                    for (int i = 0; i < actions.getContractActionsCount(); i++) {
                        var action = actions.getContractActions(i);
                        actionsMap.put(new ContractAction.Id(recordItem.getConsensusTimestamp(), i), action);
                    }
                    return actionsMap;
                })
                .flatMap(actionsMap -> actionsMap.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(contractActionRepository.findAll())
                .hasSize(expected.size())
                .allSatisfy(a -> assertAll(
                                () -> assertThat(a)
                                        .satisfies(actual -> assertThat(actual.getCaller()).isNotNull())
                                        .satisfies(actual -> assertThat(actual.getResultData()).isNotEmpty()),
                                () -> assertThat(expected.get(a.getId()))
                                        .isNotNull()
                                        .returns(a.getCallDepth(), e -> e.getCallDepth())
                                        .returns(a.getCallOperationType(), e -> e.getCallOperationTypeValue())
                                        .returns(a.getCallType(), e -> e.getCallTypeValue())
                                        .returns(a.getConsensusTimestamp(), e -> recordItem.getConsensusTimestamp())
                                        .returns(a.getGas(), e -> e.getGas())
                                        .returns(a.getGasUsed(), e -> e.getGasUsed())
                                        .returns(a.getCallerType(),
                                                e -> e.getCallerCase() == CALLING_CONTRACT ? CONTRACT : ACCOUNT)
                                        .returns(a.getPayerAccountId(), e -> recordItem.getPayerAccountId())
                                        .returns(a.getResultDataType(), e -> e.getResultDataCase().getNumber())
                                        .returns(a.getValue(), e -> e.getValue())
                                        .satisfiesAnyOf(e -> assertThat(a.getRecipientContract()).isNotNull(),
                                                e -> assertThat(a.getRecipientAccount()).isNotNull(),
                                                e -> assertThat(a.getRecipientAddress()).isNotEmpty(),
                                                e -> assertThat(e.getRecipientCase()).isEqualTo(RECIPIENT_NOT_SET))
                        )
                );
    }

    private void assertContractRuntimeBytecode(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords()
                .stream()
                .filter(TransactionSidecarRecord::getMigration)
                .filter(TransactionSidecarRecord::hasBytecode)
                .map(TransactionSidecarRecord::getBytecode)
                .map(b -> Contract.builder()
                        .id(EntityId.of(b.getContractId()).getId())
                        .runtimeBytecode(DomainUtils.toBytes(b.getRuntimeBytecode()))
                        .build())
                .toList();
        assertThat(contractRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "runtimeBytecode")
                .containsAll(expected);
    }

    private void assertContractLogs(RecordItem recordItem) {
        assertContractLogs(recordItem, false);
    }

    private void assertContractLogs(RecordItem recordItem, boolean ethereum) {
        var contractFunctionResult = getFunctionResult(recordItem);
        var listAssert = assertThat(contractLogRepository.findAll())
                .hasSize(contractFunctionResult.getLogInfoCount());
        var transactionHash = ethereum ? recordItem.getEthereumTransaction().getHash() :
                Arrays.copyOfRange(transaction.getTransactionHash(), 0, 32);
        Integer transactionIndex = transaction.getIndex();

        if (contractFunctionResult.getLogInfoCount() > 0) {
            var blooms = new ArrayList<byte[]>();
            var contractIds = new ArrayList<EntityId>();
            var data = new ArrayList<byte[]>();
            contractFunctionResult.getLogInfoList().forEach(x -> {
                blooms.add(DomainUtils.toBytes(x.getBloom()));
                contractIds.add(EntityId.of(x.getContractID()));
                data.add(DomainUtils.toBytes(x.getData()));
            });

            listAssert.extracting(ContractLog::getPayerAccountId).containsOnly(recordItem.getPayerAccountId());
            listAssert.extracting(ContractLog::getContractId).containsAll(contractIds);
            listAssert.extracting(ContractLog::getRootContractId)
                    .containsOnly(EntityId.of(contractFunctionResult.getContractID()));
            listAssert.extracting(ContractLog::getConsensusTimestamp).containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractLog::getIndex).containsExactlyInAnyOrder(0, 1);
            listAssert.extracting(ContractLog::getBloom).containsAll(blooms);
            listAssert.extracting(ContractLog::getData).containsAll(data);
            listAssert.extracting(ContractLog::getTransactionHash).containsOnly(transactionHash);
            listAssert.extracting(ContractLog::getTransactionIndex).containsOnly(transactionIndex);
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        var contractStateChanges = recordItem.getSidecarRecords()
                .stream()
                .filter(TransactionSidecarRecord::hasStateChanges)
                .flatMap(r -> r.getStateChanges().getContractStateChangesList().stream()
                        .flatMap(protoStateChange -> {
                            var template = ContractStateChange.builder()
                                    .consensusTimestamp(recordItem.getConsensusTimestamp())
                                    .contractId(EntityId.of(protoStateChange.getContractId()).getId())
                                    .migration(r.getMigration())
                                    .payerAccountId(recordItem.getPayerAccountId())
                                    .build();
                            return protoStateChange.getStorageChangesList().stream()
                                    .map(storageChange -> template.toBuilder()
                                            .slot(DomainUtils.toBytes(storageChange.getSlot()))
                                            .valueRead(DomainUtils.toBytes(storageChange.getValueRead()))
                                            .valueWritten(DomainUtils.toBytes(
                                                    storageChange.getValueWritten().getValue()))
                                            .build()
                                    );
                        })
                )
                .toList();

        var contractStates = contractStateChanges.stream()
                .filter(c -> c.getValueWritten() != null)
                .map(c -> ContractState.builder()
                        .contractId(c.getContractId())
                        .createdTimestamp(c.getConsensusTimestamp())
                        .modifiedTimestamp(c.getConsensusTimestamp())
                        .slot(DomainUtils.leftPadBytes(c.getSlot(), 32))
                        .value(c.getValueWritten())
                        .build())
                .toList();

        assertThat(contractStateChangeRepository.findAll()).containsExactlyInAnyOrderElementsOf(contractStateChanges);
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(contractStates);
    }

    protected void process(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        transaction = domainBuilder.transaction()
                .customize(t -> t.entityId(entityId).type(recordItem.getTransactionType()))
                .get();

        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItem.getConsensusTimestamp();
            RecordFile recordFile = domainBuilder.recordFile().customize(x -> x
                            .consensusStart(consensusStart)
                            .consensusEnd(consensusStart + 1)
                            .name(filename))
                    .get();

            recordStreamFileListener.onStart();
            contractResultService.process(recordItem, transaction);
            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
        });
    }

    private ByteString getFailedInitcode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            return ByteString.EMPTY;
        }

        var failedInitcode = recordItem.getTransactionBody().getContractCreateInstance().getInitcode();
        if (failedInitcode.isEmpty()) {
            failedInitcode = recordItem.getSidecarRecords()
                    .stream()
                    .filter(TransactionSidecarRecord::hasBytecode)
                    .map(s -> s.getBytecode().getInitcode())
                    .findFirst()
                    .orElse(ByteString.EMPTY);
        }

        return failedInitcode;
    }

    private ContractFunctionResult getFunctionResult(RecordItem recordItem) {
        var record = recordItem.getTransactionRecord();
        return record.hasContractCreateResult() ? record.getContractCreateResult() : record.getContractCallResult();
    }

    private com.hedera.services.stream.proto.ContractAction contractAction(Long callingContractNum,
                                                                           CallOperationType callOperationType,
                                                                           ContractActionType contractActionType,
                                                                           Long recipientContractNum) {
        return com.hedera.services.stream.proto.ContractAction.newBuilder()
                .setCallDepth(1)
                .setCallingContract(ContractID.newBuilder().setContractNum(callingContractNum))
                .setCallOperationType(callOperationType)
                .setCallType(contractActionType)
                .setGas(100)
                .setGasUsed(50)
                .setInput(bytes(100))
                .setRecipientContract(ContractID.newBuilder().setContractNum(recipientContractNum))
                .setOutput(bytes(256))
                .setValue(20)
                .build();
    }

    private byte[] toBytes(ByteString byteString) {
        return byteString == ByteString.EMPTY ? null : DomainUtils.toBytes(byteString);
    }

    private ByteString bytes(int length) {
        byte[] bytes = randomBytes(length);
        return ByteString.copyFrom(bytes);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String parseContractResultStrings(String message) {
        return StringUtils.isEmpty(message) ? null : message;
    }

    private Long parseContractResultLongs(long num) {
        return num == 0 ? null : num;
    }
}
