package com.hedera.mirror.importer.parser.record.entity;

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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hedera.services.stream.proto.ContractAction.CallerCase.CALLING_CONTRACT;
import static com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody.StakedIdCase.STAKEDID_NOT_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Version;

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
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.repository.ContractStateRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.ContractBytecode;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final Version HAPI_VERSION_0_23_0 = new Version(0, 23, 0);
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;

    // saves the mapping from proto ContractID to EntityId so as not to use EntityIdService to verify itself
    private Map<ContractID, EntityId> contractIds;

    @BeforeEach
    void before() {
        contractIds = new HashMap<>();
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false, false"})
    void contractCreate(boolean bytecodeSourceFileId, boolean hasAutoRenewAccount) {
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> {
                    if (!bytecodeSourceFileId) {
                        b.clearFileID().setInitcode(recordItemBuilder.bytes(1));
                    }
                    if (!hasAutoRenewAccount) {
                        b.clearAutoRenewAccountId();
                    }
                    b.clearAutoRenewAccountId()
                            .setDeclineReward(true)
                            .setStakedAccountId(AccountID.newBuilder().setAccountNum(1L).build());
                })
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, record),
                () -> assertContractStateChanges(recordItem)
        );
    }

    // Issue #5637 caused by an invalid receipt.ContractID sent by consensus nodes in testnet.
    @Test
    void contractCreateWithInvalidId() {
        var invalidId = ContractID.newBuilder()
                .setShardNum(-382413634L)
                .setRealmNum(-4713217343126473096L)
                .setContractNum(-9016639978277801310L)
                .build();
        var recordItem = recordItemBuilder.contractCreate()
                .receipt(r -> r.setContractID(invalidId))
                .record(r -> r.getContractCreateResultBuilder().setContractID(invalidId))
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractStateChanges(recordItem),
                () -> assertThat(transactionRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(null, com.hedera.mirror.common.domain.transaction.Transaction::getEntityId),
                () -> assertThat(contractResultRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(0L, ContractResult::getContractId)
        );
    }

    @Test
    void contractCreateWithEvmAddress() {
        // no child tx, creates a single contract with evm address set
        byte[] evmAddress = domainBuilder.evmAddress();
        RecordItem recordItem = recordItemBuilder.contractCreate(CONTRACT_ID)
                .transactionBody(t -> t.setDeclineReward(false).setStakedNodeId(1L))
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs()
                        .addCreatedContractIDs(CONTRACT_ID)
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(evmAddress)))
                ))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_23_0))
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, record),
                () -> assertContractStateChanges(recordItem)
        );
    }

    @Test
    void contractCreateWithEvmAddressAndChildCreate() {
        // given contractCreate with child contractCreate
        var parentEvmAddress = domainBuilder.evmAddress();
        var parentRecordItem = recordItemBuilder.contractCreate()
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(parentEvmAddress)))
                ))
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_23_0))
                .sidecarRecords(s -> s.remove(0))
                .build();

        var contractCreateResult = parentRecordItem.getTransactionRecord().getContractCreateResult();
        var childContractId = contractCreateResult.getCreatedContractIDsList().stream()
                .filter(c -> !c.equals(contractCreateResult.getContractID()))
                .findFirst().get();
        var childEvmAddress = domainBuilder.evmAddress();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childTransactionId = parentRecordItem.getTransactionRecord().getTransactionID().toBuilder().setNonce(1);
        var childRecordItem = recordItemBuilder.contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setTransactionID(childTransactionId)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress)))))
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_23_0))
                .sidecarRecords(s -> s.remove(0))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        // then
        var parentTransactionBody = parentRecordItem.getTransactionBody().getContractCreateInstance();
        var childTransactionBody = childRecordItem.getTransactionBody().getContractCreateInstance();
        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertContractEntity(parentRecordItem),
                () -> assertContractEntity(childRecordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(2),
                () -> assertContractCreateResult(parentTransactionBody, parentRecordItem.getTransactionRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getTransactionRecord()),
                () -> assertContractStateChanges(parentRecordItem),
                () -> assertContractStateChanges(childRecordItem)
        );
    }

    @Test
    void contractCreateFailedWithResult() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(r -> r.setContractCreateResult(ContractFunctionResult.getDefaultInstance()))
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateFailedWithoutResult() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateDoNotPersist() {
        entityProperties.getPersist().setContracts(false);

        RecordItem recordItem = recordItemBuilder.contractCreate().build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEntities()
                , () -> assertTransactionAndRecord(transactionBody, record)
                , () -> assertFalse(getContractResult(record.getConsensusTimestamp()).isPresent())
        );
    }

    @ParameterizedTest
    @CsvSource({"PLAIN, 5005, 5005", "PARSABLE_EVM, 0, 0", "CREATE2_EVM, , 5002"})
    void contractUpdateAllToExisting(ContractIdType contractIdType, Long newAutoRenewAccount,
                                     Long expectedAutoRenewAccount) {
        // first create the contract
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true, c -> {
            c.obtainerId(null)
                    .declineReward(true)
                    .stakedAccountId(1L);
            if (newAutoRenewAccount == null) {
                c.autoRenewAccountId(expectedAutoRenewAccount);
            }
        });
        Entity entity = setupResult.entity;

        // now update
        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true, b -> {
            b.setDeclineReward(BoolValue.of(false));
            if (newAutoRenewAccount != null) {
                b.getAutoRenewAccountIdBuilder().setAccountNum(newAutoRenewAccount);
            }
        });
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        var recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(expectedAutoRenewAccount, Entity::getAutoRenewAccountId)
                        .returns(entity.getCreatedTimestamp(), Entity::getCreatedTimestamp)
                        .returns(false, Entity::getDeclineReward)
        );
    }

    @Test
    void contractUpdateAllWithMemoToExisting() {
        // first create the contract
        EntityId contractId = EntityId.of(CONTRACT_ID);
        Entity contract = domainBuilder.entity()
                .customize(c ->
                        c.obtainerId(null)
                                .id(contractId.getId())
                                .num(contractId.getEntityNum())
                                .stakedNodeId(1L)
                                .type(CONTRACT)
                )
                .persist();

        // now update
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(contract.getCreatedTimestamp(), Entity::getCreatedTimestamp)
        );
    }

    @ParameterizedTest
    @EnumSource(value = ContractIdType.class, names = {"PLAIN", "PARSABLE_EVM"})
    void contractUpdateAllToNew(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false, false, c -> c.obtainerId(null));

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(null, Entity::getCreatedTimestamp)
        );
    }

    @Test
    void contractUpdateAllToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.entity.toEntityId())
        );
    }

    @Test
    void contractUpdateAllWithMemoToNew() {
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(null, Entity::getCreatedTimestamp)
        );
    }

    @ParameterizedTest
    @EnumSource(value = ContractIdType.class)
    void contractUpdateAllToExistingInvalidTransaction(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                ContractTransactionType.UPDATE);
        RecordItem recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractStateChanges(recordItem),
                () -> assertThat(entityRepository.findAll()).containsExactly(setupResult.entity)
        );
    }

    @ParameterizedTest
    @CsvSource({"PLAIN, false", "PARSABLE_EVM,true", "CREATE2_EVM,false"})
    void contractDeleteToExisting(ContractIdType contractIdType, boolean permanentRemoval) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, permanentRemoval);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();

        parseRecordItemAndCommit(recordItem);

        Entity dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbContractEntity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(EntityId.of(PAYER), Entity::getObtainerId)
                        .returns(permanentRemoval, Entity::getPermanentRemoval)
                        .returns(Range.atLeast(recordItem.getConsensusTimestamp()), Entity::getTimestampRange)
        );
    }

    @ParameterizedTest
    @EnumSource(value = ContractIdType.class, names = {"PLAIN", "PARSABLE_EVM"})
    void contractDeleteToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work for PLAIN and PARSABLE_EVM
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();

        parseRecordItemAndCommit(recordItem);
        Entity entity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(entity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                        .returns(null, Entity::getAutoRenewPeriod)
                        .returns(null, Entity::getExpirationTimestamp)
                        .returns(null, Entity::getKey)
                        .returns(EntityId.of(PAYER), Entity::getObtainerId)
                        .returns(null, Entity::getProxyAccountId)

        );
    }

    @Test
    void contractDeleteToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.entity.toEntityId())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                ContractTransactionType.DELETE);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(transactionBody.getContractDeleteInstance().getContractID()).isNotNull()
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToExisting(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        var recordItem = recordItemBuilder.contractCall(CONTRACT_ID).build();
        var transactionBody = recordItem.getTransactionBody();
        var record = recordItem.getTransactionRecord();
        var contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractCallResult(contractCallTransactionBody, record),
                () -> assertContractStateChanges(recordItem),
                () -> assertContractAction(recordItem),
                () -> assertThat(entityRepository.findAll()).contains(setupResult.entity)
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToExistingWithChildContractCreate(ContractIdType contractIdType) {
        // given contractCall with child contractCreate
        var setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);
        var parentId = setupResult.entity.toEntityId();

        var parentRecordItem = recordItemBuilder.contractCall()
                .receipt(r -> r.setContractID(CONTRACT_ID))
                .transactionBody(b -> b.setContractID(setupResult.protoContractId))
                .record(r -> r.clearContractCallResult()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)))
                .sidecarRecords(s -> s.remove(0))
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_23_0))
                .build();

        var childEvmAddress = domainBuilder.evmAddress();
        var record = parentRecordItem.getTransactionRecord();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childContractId = record.getContractCallResult().getCreatedContractIDs(0);
        var childTransactionId = record.getTransactionID().toBuilder().setNonce(1).build();
        var childRecordItem = recordItemBuilder.contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress))))
                        .setTransactionID(childTransactionId))
                .sidecarRecords(s -> s.remove(0))
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_23_0))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        // then
        var parentTransactionBody = parentRecordItem.getTransactionBody().getContractCall();
        var childTransactionBody = childRecordItem.getTransactionBody().getContractCreateInstance();
        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertEntities(parentId, EntityId.of(childContractId)),
                () -> assertTransactionAndRecord(parentRecordItem.getTransactionBody(),
                        parentRecordItem.getTransactionRecord()),
                () -> assertTransactionAndRecord(childRecordItem.getTransactionBody(),
                        childRecordItem.getTransactionRecord()),
                () -> assertThat(entityRepository.findAll()).contains(setupResult.entity),
                () -> assertCreatedContract(childRecordItem),
                () -> assertContractCallResult(parentTransactionBody, parentRecordItem.getTransactionRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getTransactionRecord()),
                () -> assertContractStateChanges(parentRecordItem),
                () -> assertContractStateChanges(childRecordItem)
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work. Note for the create2 evm address,
        // ContractCallTransactionHandler will get the correct plain contractId from the transaction record instead
        // only cache the create2 evm address to verify it later
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false,
                contractIdType == ContractIdType.CREATE2_EVM);

        Transaction transaction = contractCallTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.CALL);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();
        RecordItem recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CREATED_CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractCallResult(contractCallTransactionBody, record),
                () -> assertContractStateChanges(recordItem)
        );
    }

    @Test
    void contractCallTokenPrecompiles() {
        // given
        RecordItem recordItemCall = recordItemBuilder.contractCall().build();
        parseRecordItemAndCommit(recordItemCall);

        var parentConsensusTimestamp = recordItemCall.getTransactionRecord().getConsensusTimestamp();
        var parentTransactionId = recordItemCall.getTransactionBody().getTransactionID();
        var childTransactionId = parentTransactionId.toBuilder().setNonce(1).build();
        var payerAccount = recordItemCall.getPayerAccountId();
        var validStart = DomainUtils.timeStampInNanos(parentTransactionId.getTransactionValidStart());

        // when
        RecordItem recordItemMint = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON)
                .transactionBodyWrapper(b -> b.setTransactionID(childTransactionId))
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .build();
        parseRecordItemAndCommit(recordItemMint);

        // then
        assertEquals(2, transactionRepository.count());
        assertThat(transactionRepository.findById(recordItemCall.getConsensusTimestamp()))
                .get()
                .returns(payerAccount, com.hedera.mirror.common.domain.transaction.Transaction::getPayerAccountId)
                .returns(validStart, com.hedera.mirror.common.domain.transaction.Transaction::getValidStartNs)
                .returns(0, com.hedera.mirror.common.domain.transaction.Transaction::getNonce)
                .returns(null, com.hedera.mirror.common.domain.transaction.Transaction::getParentConsensusTimestamp);

        assertThat(transactionRepository.findById(recordItemMint.getConsensusTimestamp()))
                .get()
                .returns(payerAccount, com.hedera.mirror.common.domain.transaction.Transaction::getPayerAccountId)
                .returns(validStart, com.hedera.mirror.common.domain.transaction.Transaction::getValidStartNs)
                .returns(1, com.hedera.mirror.common.domain.transaction.Transaction::getNonce)
                .returns(recordItemCall.getConsensusTimestamp(),
                        com.hedera.mirror.common.domain.transaction.Transaction::getParentConsensusTimestamp);
    }

    @Test
    void contractCallFailedWithResult() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(r -> r.setContractCreateResult(ContractFunctionResult.getDefaultInstance()))
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertFailedContractCallTransaction(transactionBody, record)
        );
    }

    @Test
    void contractCallFailedWithoutResult() {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE, ContractTransactionType.CALL)
                .toBuilder().clearContractCallResult().build();

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertFailedContractCallTransaction(transactionBody, record)
        );
    }

    @Test
    void contractCallDoNotPersist() {
        entityProperties.getPersist().setContracts(false);
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.CALL);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    // Test for bad entity id in a failed transaction
    @Test
    void contractCallBadContractId() {
        var badContractId = ContractID.newBuilder().setContractNum(-1L).build();
        var recordItem = recordItemBuilder.contractCall(badContractId)
                .record(r -> r.clearContractCallResult())
                .sidecarRecords(s -> s.clear())
                .status(ResponseCodeEnum.INVALID_CONTRACT_ID)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.INVALID_CONTRACT_ID))
                .build();
        var record = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    private void assertFailedContractCreate(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        var contractCreateBody = transactionBody.getContractCreateInstance();
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(contractCreateBody.getInitialBalance(),
                        dbTransaction.getInitialBalance()),
                () -> assertPartialContractCreateResult(transactionBody.getContractCreateInstance(), record));
    }

    private void assertFailedContractCallTransaction(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        var contractCallBody = transactionBody.getContractCall();
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isNotNull(),
                () -> assertEquals(EntityId.of(contractCallBody.getContractID()),
                        dbTransaction.getEntityId()),
                () -> assertPartialContractCallResult(contractCallBody, record));
    }

    private void assertContractEntity(RecordItem recordItem) {
        long createdTimestamp = recordItem.getConsensusTimestamp();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var adminKey = transactionBody.getAdminKey().toByteArray();
        var transaction = transactionRepository.findById(createdTimestamp).get();
        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress() ?
                DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()) : null;
        EntityId entityId = transaction.getEntityId();
        Entity entity = getEntity(entityId);
        Long expectedAutoRenewAccountId = transactionBody.hasAutoRenewAccountId() ?
                transactionBody.getAutoRenewAccountId().getAccountNum() : null;
        EntityId expectedFileId = transactionBody.hasFileID() ? EntityId.of(transactionBody.getFileID()) : null;

        ContractBytecode sidecarBytecode = null;
        var sidecarRecords = recordItem.getSidecarRecords();
        var contractId = contractCreateResult.getContractID();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasBytecode() && contractId.equals(sidecarRecord.getBytecode().getContractId())) {
                sidecarBytecode = sidecarRecord.getBytecode();
                break;
            }
        }
        byte[] expectedInitcode = sidecarBytecode != null ? sidecarBytecode.getInitcode().toByteArray() :
                null;
        expectedInitcode = transactionBody.getInitcode() != ByteString.EMPTY ?
                DomainUtils.toBytes(transactionBody.getInitcode()) : expectedInitcode;

        assertThat(transaction)
                .isNotNull()
                .returns(transactionBody.getInitialBalance(), t -> t.getInitialBalance());

        assertThat(entity)
                .isNotNull()
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(createdTimestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(evmAddress, Entity::getEvmAddress)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(entityId.getId(), Entity::getId)
                .returns(adminKey, Entity::getKey)
                .returns(transactionBody.getMaxAutomaticTokenAssociations(), Entity::getMaxAutomaticTokenAssociations)
                .returns(transactionBody.getMemo(), Entity::getMemo)
                .returns(createdTimestamp, Entity::getTimestampLower)
                .returns(null, Entity::getObtainerId)
                .returns(EntityId.of(transactionBody.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Entity::getPublicKey)
                .returns(CONTRACT, Entity::getType);

        assertThat(contractRepository.findById(entity.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId)
                .returns(expectedInitcode, Contract::getInitcode);

        if (entityProperties.getPersist().isContracts()) {
            assertCreatedContract(recordItem);
        }
    }

    private void assertCreatedContract(RecordItem recordItem) {
        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress() ?
                DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()) : null;
        EntityId createdId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var contractAssert = assertThat(entityRepository.findById(createdId.getId()))
                .get()
                .returns(recordItem.getConsensusTimestamp(), Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(evmAddress, Entity::getEvmAddress)
                .returns(createdId.getId(), Entity::getId)
                .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                .returns(createdId.getEntityNum(), Entity::getNum)
                .returns(createdId.getShardNum(), Entity::getShard)
                .returns(createdId.getType(), Entity::getType);

        var contractCreateInstance = recordItem.getTransactionBody().getContractCreateInstance();
        contractAssert.returns(contractCreateInstance.getDeclineReward(), Entity::getDeclineReward);

        if (contractCreateInstance.getStakedIdCase() == ContractCreateTransactionBody.StakedIdCase.STAKEDID_NOT_SET) {
            return;
        }

        contractAssert.returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);

        if (contractCreateInstance.hasStakedAccountId()) {
            var accountId = EntityId.of(contractCreateInstance.getStakedAccountId());
            contractAssert.returns(accountId.getId(), Entity::getStakedAccountId)
                    .returns(-1L, Entity::getStakedNodeId);
        } else {
            contractAssert.returns(contractCreateInstance.getStakedNodeId(), Entity::getStakedNodeId)
                    .returns(null, Entity::getStakedAccountId);
        }
    }

    private void assertContractAction(RecordItem recordItem) {
        int count = 0;
        var repositoryActions = assertThat(contractActionRepository.findAll());

        var contractActions = new ArrayList<com.hedera.services.stream.proto.ContractAction>();
        var sidecarRecords = recordItem.getSidecarRecords();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasActions()) {
                var actions = sidecarRecord.getActions();
                for (int j = 0; j < actions.getContractActionsCount(); j++) {
                    contractActions.add(actions.getContractActions(j));
                }
            }
        }

        for (var contractAction : contractActions) {
            var caller = contractAction.getCallerCase().equals(CALLING_CONTRACT) ?
                    EntityId.of(contractAction.getCallingContract()) :
                    EntityId.of(contractAction.getCallingAccount());

            repositoryActions.filteredOn(c -> c.getConsensusTimestamp() == recordItem.getConsensusTimestamp()
                            && c.getCaller().equals(caller)
                    )
                    .hasSize(1)
                    .first()
                    .returns(contractAction.getCallDepth(), ContractAction::getCallDepth)
                    .returns(contractAction.getCallOperationTypeValue(), ContractAction::getCallOperationType)
                    .returns(contractAction.getCallTypeValue(), ContractAction::getCallType)
                    .returns(contractAction.getGas(), ContractAction::getGas)
                    .returns(contractAction.getGasUsed(), ContractAction::getGasUsed)
                    .returns(contractAction.getInput().toByteArray(), ContractAction::getInput)
                    .returns(contractAction.getResultDataCase().getNumber(), ContractAction::getResultDataType)
                    .returns(contractAction.getValue(), ContractAction::getValue)
                    .satisfies(c -> assertThat(c.getCaller()).isNotNull())
                    .satisfies(c -> assertThat(c.getResultData()).isNotEmpty())
                    .satisfiesAnyOf(c -> assertThat(c.getRecipientContract()).isNotNull(),
                            c -> assertThat(c.getRecipientAccount()).isNotNull(),
                            c -> assertThat(c.getRecipientAddress()).isNotEmpty());
            ++count;
        }

        repositoryActions.hasSize(count);
    }

    private ObjectAssert<Entity> assertContractEntity(ContractUpdateTransactionBody expected,
                                                      Timestamp consensusTimestamp) {
        Entity entity = getTransactionEntity(consensusTimestamp);
        long updatedTimestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        var adminKey = expected.getAdminKey().toByteArray();
        var expectedMaxAutomaticTokenAssociations = expected.hasMaxAutomaticTokenAssociations() ?
                expected.getMaxAutomaticTokenAssociations().getValue() : null;

        var contractAssert = assertThat(entity)
                .isNotNull()
                .returns(expected.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(false, Entity::getDeleted)
                .returns(DomainUtils.timeStampInNanos(expected.getExpirationTime()), Entity::getExpirationTimestamp)
                .returns(adminKey, Entity::getKey)
                .returns(expectedMaxAutomaticTokenAssociations, Entity::getMaxAutomaticTokenAssociations)
                .returns(getMemoFromContractUpdateTransactionBody(expected), Entity::getMemo)
                .returns(updatedTimestamp, Entity::getTimestampLower)
                .returns(null, Entity::getObtainerId)
                .returns(EntityId.of(expected.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Entity::getPublicKey)
                .returns(CONTRACT, Entity::getType);

        if (expected.getStakedIdCase() == STAKEDID_NOT_SET) {
            return contractAssert;
        }

        if (expected.hasStakedAccountId()) {
            long expectedAccountId = EntityId.of(expected.getStakedAccountId()).getId();
            contractAssert.returns(expectedAccountId, Entity::getStakedAccountId)
                    .returns(null, Entity::getStakedNodeId)
                    .returns(-1L, Entity::getStakePeriodStart);
        } else {
            contractAssert.returns(expected.getStakedNodeId(), Entity::getStakedNodeId)
                    .returns(null, Entity::getStakedAccountId)
                    .returns(Utility.getEpochDay(DomainUtils.timestampInNanosMax(consensusTimestamp)),
                            Entity::getStakePeriodStart);
        }
        return contractAssert;
    }

    private void assertContractCreateResult(ContractCreateTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
        TransactionReceipt receipt = record.getReceipt();
        ContractFunctionResult result = record.getContractCreateResult();

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(receipt.getContractID().getContractNum(), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        if (receipt.getStatus() == ResponseCodeEnum.SUCCESS) {
            contractResult.returns(receipt.getContractID().getContractNum(), ContractResult::getContractId);
        }

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult);
    }

    private void assertContractCallResult(ContractCallTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
        ContractFunctionResult result = record.getContractCallResult();

        // get the corresponding entity id from the local cache, fall back to parseContractId if not found.
        ContractID protoContractId = record.getContractCallResult().getContractID();
        EntityId contractId = contractIds.getOrDefault(protoContractId, parseContractId(protoContractId));

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(contractId.getId(), ContractResult::getContractId)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult);
    }

    private void assertContractResult(long consensusTimestamp, ContractFunctionResult result,
                                      List<ContractLoginfo> logInfoList,
                                      ObjectAssert<ContractResult> contractResult) {
        List<Long> createdContractIds = result.getCreatedContractIDsList()
                .stream()
                .map(ContractID::getContractNum)
                .collect(Collectors.toList());

        contractResult
                .returns(result.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(result.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(createdContractIds, ContractResult::getCreatedContractIds)
                .returns(result.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(result.toByteArray(), ContractResult::getFunctionResult)
                .returns(result.getGasUsed(), ContractResult::getGasUsed);

        for (int i = 0; i < logInfoList.size(); i++) {
            int index = i;
            ContractLoginfo logInfo = logInfoList.get(i);

            assertThat(contractLogRepository.findById(new ContractLog.Id(consensusTimestamp, index)))
                    .isPresent()
                    .get()
                    .returns(logInfo.getBloom().toByteArray(), ContractLog::getBloom)
                    .returns(consensusTimestamp, ContractLog::getConsensusTimestamp)
                    .returns(EntityId.of(logInfo.getContractID()), ContractLog::getContractId)
                    .returns(logInfo.getData().toByteArray(), ContractLog::getData)
                    .returns(index, ContractLog::getIndex)
                    .returns(EntityId.of(result.getContractID()), ContractLog::getRootContractId)
                    .returns(Utility.getTopic(logInfo, 0), ContractLog::getTopic0)
                    .returns(Utility.getTopic(logInfo, 1), ContractLog::getTopic1)
                    .returns(Utility.getTopic(logInfo, 2), ContractLog::getTopic2)
                    .returns(Utility.getTopic(logInfo, 3), ContractLog::getTopic3);
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        int count = 0;
        var contractStateChanges = assertThat(contractStateChangeRepository.findAll());

        var sidecarStateChanges = new ArrayList<com.hedera.services.stream.proto.ContractStateChange>();
        var sidecarRecords = recordItem.getSidecarRecords();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasStateChanges()) {
                var stateChanges = sidecarRecord.getStateChanges();
                for (int j = 0; j < stateChanges.getContractStateChangesCount(); j++) {
                    sidecarStateChanges.add(stateChanges.getContractStateChanges(j));
                }
            }
        }

        for (var contractStateChange : sidecarStateChanges) {
            EntityId contractId = EntityId.of(contractStateChange.getContractId());
            for (var storageChange : contractStateChange.getStorageChangesList()) {
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten() ? storageChange.getValueWritten().getValue()
                        .toByteArray() : null;

                contractStateChanges.filteredOn(c -> c.getConsensusTimestamp() == recordItem.getConsensusTimestamp()
                                && c.getContractId() == contractId.getId() && Arrays.equals(c.getSlot(), slot))
                        .hasSize(1)
                        .first()
                        .returns(storageChange.getValueRead().toByteArray(), ContractStateChange::getValueRead)
                        .returns(valueWritten, ContractStateChange::getValueWritten);
                ++count;
            }
        }

        contractStateChanges.hasSize(count);

        assertContractState(sidecarStateChanges, recordItem.getConsensusTimestamp());
    }

    private void assertContractState(ArrayList<com.hedera.services.stream.proto.ContractStateChange> sidecarStateChanges,
                                     long consensusTimestamp) {
        int count = 0;
        var contractStates = assertThat(contractStateRepository.findAll());
        for (var contractStateChange : sidecarStateChanges) {
            EntityId contractId = EntityId.of(contractStateChange.getContractId());
            for (var storageChange : contractStateChange.getStorageChangesList()) {
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten() ? storageChange.getValueWritten().getValue()
                        .toByteArray() : null;

                if (valueWritten != null) {
                    contractStates.filteredOn(c -> c.getModifiedTimestamp() == consensusTimestamp
                                    && c.getContractId() == contractId.getId() && Arrays.equals(c.getSlot(), slot))
                            .hasSize(1)
                            .first()
                            .returns(DomainUtils.leftPadBytes(slot, 32), ContractState::getSlot);
                    ++count;
                }
            }
        }

        contractStates.hasSize(count);
    }

    private void assertPartialContractCreateResult(ContractCreateTransactionBody transactionBody,
                                                   TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertPartialContractResult(contractResult);
    }

    private void assertPartialContractCallResult(ContractCallTransactionBody transactionBody,
                                                 TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(transactionBody.getContractID().getContractNum(), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertPartialContractResult(contractResult);
    }

    private void assertPartialContractResult(ObjectAssert<ContractResult> contractResult) {
        contractResult
                .returns(null, ContractResult::getBloom)
                .returns(null, ContractResult::getCallResult)
                .returns(List.of(), ContractResult::getCreatedContractIds)
                .returns(null, ContractResult::getErrorMessage)
                .returns(null, ContractResult::getFunctionResult)
                .returns(null, ContractResult::getGasUsed);
    }

    private TransactionRecord getContractTransactionRecord(TransactionBody transactionBody,
                                                           ContractTransactionType contractTransactionType) {
        return getContractTransactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, contractTransactionType);
    }

    private TransactionRecord getContractTransactionRecord(TransactionBody transactionBody, ResponseCodeEnum status,
                                                           ContractTransactionType contractTransactionType) {
        return buildTransactionRecord(recordBuilder -> {
            recordBuilder.getReceiptBuilder().setContractID(CONTRACT_ID);

            switch (contractTransactionType) {
                case CREATE:
                    buildContractFunctionResult(recordBuilder.getContractCreateResultBuilder());
                    break;
                case CALL:
                    var contractFunctionResult = recordBuilder.getContractCallResultBuilder();
                    buildContractFunctionResult(contractFunctionResult);
                    contractFunctionResult.removeCreatedContractIDs(0);
                    break;
                default:
                    break;
            }
        }, transactionBody, status.getNumber());
    }

    private Transaction contractUpdateAllTransaction(boolean setMemoWrapperOrMemo) {
        return contractUpdateAllTransaction(CONTRACT_ID, setMemoWrapperOrMemo);
    }

    private Transaction contractUpdateAllTransaction(ContractID contractId, boolean setMemoWrapperOrMemo) {
        return contractUpdateAllTransaction(contractId, setMemoWrapperOrMemo, b -> {
        });
    }

    private Transaction contractUpdateAllTransaction(ContractID contractId, boolean setMemoWrapperOrMemo,
                                                     Consumer<ContractUpdateTransactionBody.Builder> customizer) {
        return buildTransaction(builder -> {
            ContractUpdateTransactionBody.Builder contractUpdate = builder.getContractUpdateInstanceBuilder();
            contractUpdate.setAdminKey(keyFromString(KEY));
            contractUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(400).build());
            contractUpdate.setContractID(contractId);
            contractUpdate.setExpirationTime(Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
            contractUpdate.setFileID(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());
            contractUpdate.setMaxAutomaticTokenAssociations(Int32Value.of(100));
            if (setMemoWrapperOrMemo) {
                contractUpdate.setMemoWrapper(StringValue.of("contract update memo"));
            } else {
                contractUpdate.setMemo("contract update memo");
            }
            contractUpdate.setProxyAccountID(PROXY_UPDATE);
            customizer.accept(contractUpdate);
        });
    }

    private Transaction contractDeleteTransaction(ContractID contractId, boolean permanentRemoval) {
        return buildTransaction(builder -> builder.getContractDeleteInstanceBuilder()
                .setContractID(contractId)
                .setPermanentRemoval(permanentRemoval)
                .setTransferAccountID(PAYER));
    }

    private Transaction contractDeleteTransaction() {
        return contractDeleteTransaction(CONTRACT_ID, false);
    }

    private Transaction contractCallTransaction() {
        return contractCallTransaction(CONTRACT_ID);
    }

    private Transaction contractCallTransaction(ContractID contractId) {
        return buildTransaction(builder -> {
            ContractCallTransactionBody.Builder contractCall = builder.getContractCallBuilder();
            contractCall.setAmount(88889);
            contractCall.setContractID(contractId);
            contractCall.setFunctionParameters(ByteString.copyFromUtf8("Call Parameters"));
            contractCall.setGas(33333);
        });
    }

    private Optional<ContractResult> getContractResult(Timestamp consensusTimestamp) {
        return contractResultRepository.findById(DomainUtils.timeStampInNanos(consensusTimestamp));
    }

    private String getMemoFromContractUpdateTransactionBody(ContractUpdateTransactionBody body) {
        switch (body.getMemoFieldCase()) {
            case MEMOWRAPPER:
                return body.getMemoWrapper().getValue();
            case MEMO:
                return body.getMemo();
            default:
                return null;
        }
    }

    private ContractID getContractId(ContractID contractId, byte[] evmAddress) {
        if (evmAddress == null) {
            return contractId;
        }

        return contractId.toBuilder().clearContractNum().setEvmAddress(ByteString.copyFrom(evmAddress)).build();
    }

    private byte[] getEvmAddress(ContractIdType contractIdType, EntityId contractId) {
        switch (contractIdType) {
            case PARSABLE_EVM:
                return DomainUtils.toEvmAddress(contractId);
            case CREATE2_EVM:
                return domainBuilder.evmAddress();
            default:
                return null;
        }
    }

    private EntityId parseContractId(ContractID contractId) {
        switch (contractId.getContractCase()) {
            case CONTRACTNUM:
                return EntityId.of(contractId);
            case EVM_ADDRESS:
                ByteBuffer buffer = ByteBuffer.wrap(DomainUtils.toBytes(contractId.getEvmAddress()));
                long shard = buffer.getInt();
                long realm = buffer.getLong();
                long num = buffer.getLong();
                if (shard == contractId.getShardNum() && realm == contractId.getRealmNum()) {
                    return EntityId.of(shard, realm, num, CONTRACT);
                }

                // the create2 evm address
                return null;
            default:
                return null;
        }
    }

    private SetupResult setupContract(ContractID contractId, ContractIdType contractIdType, boolean persist,
                                      boolean cache) {
        return setupContract(contractId, contractIdType, persist, cache, null);
    }

    private SetupResult setupContract(ContractID contractId, ContractIdType contractIdType, boolean persist,
                                      boolean cache, Consumer<Entity.EntityBuilder> customizer) {
        EntityId entityId = EntityId.of(contractId);
        byte[] evmAddress = getEvmAddress(contractIdType, entityId);
        ContractID protoContractId = getContractId(CONTRACT_ID, evmAddress);
        var builder = domainBuilder.entity()
                .customize(c -> c.evmAddress(evmAddress)
                        .id(entityId.getId())
                        .num(entityId.getEntityNum())
                        .type(CONTRACT));
        if (customizer != null) {
            builder.customize(customizer);
        }
        Entity entity = persist ? builder.persist() : builder.get();
        if (cache) {
            contractIds.put(protoContractId, entityId);
        }
        domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        return new SetupResult(entity, protoContractId);
    }

    enum ContractIdType {
        PLAIN,
        PARSABLE_EVM,
        CREATE2_EVM,
    }

    enum ContractTransactionType {
        CREATE,
        CALL,
        UPDATE,
        DELETE
    }

    @Value
    private static class SetupResult {
        Entity entity;
        ContractID protoContractId;
    }
}
