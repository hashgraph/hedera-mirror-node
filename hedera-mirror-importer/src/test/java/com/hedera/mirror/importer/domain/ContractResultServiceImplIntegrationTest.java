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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static com.hedera.services.stream.proto.ContractAction.CallerCase.CALLING_CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Version;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
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
import com.hedera.mirror.importer.repository.EntityRepository;
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
    private final EntityRepository entityRepository;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TransactionTemplate transactionTemplate;

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
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();
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
        var recordItem = recordItemBuilder.contractCreate().hapiVersion(new Version(0, 24, 0)).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
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
    void processContractActionInvalidRecipient() {
        var recordItem = recordItemBuilder.contractCall()
                .sidecarRecords(s -> s.get(1).getActionsBuilder().getContractActionsBuilder(0).clearRecipient())
                .build();

        assertThatThrownBy(() -> process(recordItem)).isInstanceOf(InvalidDatasetException.class)
                .hasMessageContaining("Invalid recipient");
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
        RecordItem recordItem = recordItemBuilder.contractCreate().sidecarRecords(b -> b.clear()).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
    }

    @Test
    void processContractCallFailure() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(x -> x.clearContractCallResult())
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
                .record(x -> x.clearContractCreateResult())
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
    void migrateContractSidecar() {
        // given
        var entity1 = domainBuilder.entity().persist();
        var entity2 = domainBuilder.entity().persist();
        domainBuilder.contract().customize(c -> c.id(entity1.getId())).persist();
        domainBuilder.contract().customize(c -> c.id(entity2.getId()).runtimeBytecode(null)).persist();

        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var stateChangeRecord1 = TransactionSidecarRecord.newBuilder()
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
                .setMigration(true)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(entity1.getId()))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {1})))
                .build();
        var bytecodeRecord2 = TransactionSidecarRecord.newBuilder()
                .setMigration(true)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(entity2.getId()))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {2})))
                .build();
        var bytecodeRecord3 = TransactionSidecarRecord.newBuilder()
                .setMigration(false)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(2003L))
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {3})))
                .build();

        recordItem.setSidecarRecords(List.of(stateChangeRecord1, stateChangeRecord2, bytecodeRecord1, bytecodeRecord2,
                bytecodeRecord3));

        // when
        process(recordItem);

        // then
        assertContractStateChanges(recordItem);
        assertContractRuntimeBytecode(recordItem);
        assertEntityContractType(recordItem);
    }

    private void assertEntityContractType(RecordItem recordItem) {
        var entities = entityRepository.findAll().iterator();
        recordItem.getSidecarRecords()
                .stream()
                .filter(TransactionSidecarRecord::getMigration)
                .map(TransactionSidecarRecord::getBytecode)
                .forEach(contractBytecode -> {
                    assertThat(entities).hasNext();
                    assertThat(entities.next())
                            .returns(CONTRACT, Entity::getType);
                });
        assertThat(entities).isExhausted();
    }

    @SuppressWarnings("deprecation")
    private void assertContractResult(RecordItem recordItem) {
        var functionResult = getFunctionResult(recordItem);
        var createdIds = functionResult.getCreatedContractIDsList()
                .stream()
                .map(x -> EntityId.of(x).getId())
                .collect(Collectors.toList());
        assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), ContractResult::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), ContractResult::getPayerAccountId)
                .returns(toBytes(functionResult.getBloom()), ContractResult::getBloom)
                .returns(toBytes(functionResult.getContractCallResult()), ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(parseContractResultStrings(functionResult.getErrorMessage()), ContractResult::getErrorMessage)
                .returns(parseContractResultLongs(functionResult.getGasUsed()), ContractResult::getGasUsed);
    }

    private void assertContractActions(RecordItem recordItem) {
        var contractActions = contractActionRepository.findAll().iterator();
        recordItem.getSidecarRecords()
                .stream()
                .map(TransactionSidecarRecord::getActions)
                .flatMap(c -> c.getContractActionsList().stream())
                .forEach(contractAction -> {
                    assertThat(contractActions).hasNext();
                    assertThat(contractActions.next())
                            .returns(contractAction.getCallDepth(), ContractAction::getCallDepth)
                            .returns(contractAction.getCallTypeValue(), ContractAction::getCallType)
                            .returns(recordItem.getConsensusTimestamp(), ContractAction::getConsensusTimestamp)
                            .returns(contractAction.getGas(), ContractAction::getGas)
                            .returns(contractAction.getGasUsed(), ContractAction::getGasUsed)
                            .returns(contractAction.getCallerCase() == CALLING_CONTRACT ? CONTRACT : ACCOUNT,
                                    ContractAction::getCallerType)
                            .returns(contractAction.getResultDataCase().getNumber(), ContractAction::getResultDataType)
                            .returns(contractAction.getValue(), ContractAction::getValue)
                            .satisfies(c -> assertThat(c.getCaller()).isNotNull())
                            .satisfies(c -> assertThat(c.getResultData()).isNotEmpty())
                            .satisfiesAnyOf(c -> assertThat(c.getRecipientContract()).isNotNull(),
                                    c -> assertThat(c.getRecipientAccount()).isNotNull(),
                                    c -> assertThat(c.getRecipientAddress()).isNotEmpty());
                });
        assertThat(contractActions).isExhausted();
    }

    private void assertContractRuntimeBytecode(RecordItem recordItem) {
        var contracts = contractRepository.findAll().iterator();
        recordItem.getSidecarRecords()
                .stream()
                .filter(TransactionSidecarRecord::getMigration)
                .map(TransactionSidecarRecord::getBytecode)
                .forEach(contractBytecode -> {
                    assertThat(contracts).hasNext();
                    assertThat(contracts.next())
                            .returns(DomainUtils.toBytes(contractBytecode.getRuntimeBytecode()),
                                    Contract::getRuntimeBytecode);
                });
        assertThat(contracts).isExhausted();
    }

    private void assertContractLogs(RecordItem recordItem) {
        var contractFunctionResult = getFunctionResult(recordItem);
        var listAssert = assertThat(contractLogRepository.findAll())
                .hasSize(contractFunctionResult.getLogInfoCount());

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
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        var contractIds = new ArrayList<Long>();
        var slots = new ArrayList<byte[]>();
        var valuesRead = new ArrayList<byte[]>();
        var valuesWritten = new ArrayList<byte[]>();
        var sidecarStateChanges = recordItem.getSidecarRecords()
                .stream()
                .map(TransactionSidecarRecord::getStateChanges)
                .flatMap(c -> c.getContractStateChangesList().stream())
                .peek(c -> contractIds.add(EntityId.of(c.getContractId()).getId()))
                .flatMap(c -> c.getStorageChangesList().stream())
                .collect(Collectors.toList());

        var listAssert = assertThat(contractStateChangeRepository.findAll())
                .hasSize(sidecarStateChanges.size());

        sidecarStateChanges.forEach(c -> {
            slots.add(DomainUtils.toBytes(c.getSlot()));
            valuesRead.add(DomainUtils.toBytes(c.getValueRead()));
            valuesWritten.add(DomainUtils.toBytes(c.getValueWritten().getValue()));
        });

        if (!sidecarStateChanges.isEmpty()) {
            listAssert.extracting(ContractStateChange::getPayerAccountId).containsOnly(recordItem.getPayerAccountId());
            listAssert.extracting(ContractStateChange::getContractId).containsAll(contractIds);
            listAssert.extracting(ContractStateChange::getConsensusTimestamp)
                    .containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractStateChange::getSlot).containsAll(slots);
            listAssert.extracting(ContractStateChange::getValueRead).containsAll(valuesRead);
            listAssert.extracting(ContractStateChange::getValueWritten).containsAll(valuesWritten);
        }
    }

    protected void process(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var transaction = domainBuilder.transaction()
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

    private ContractFunctionResult getFunctionResult(RecordItem recordItem) {
        var record = recordItem.getRecord();
        return record.hasContractCreateResult() ? record.getContractCreateResult() : record.getContractCallResult();
    }

    private byte[] toBytes(ByteString byteString) {
        return byteString == ByteString.EMPTY ? null : DomainUtils.toBytes(byteString);
    }

    private String parseContractResultStrings(String message) {
        return StringUtils.isEmpty(message) ? null : message;
    }

    private Long parseContractResultLongs(long num) {
        return num == 0 ? null : num;
    }
}
