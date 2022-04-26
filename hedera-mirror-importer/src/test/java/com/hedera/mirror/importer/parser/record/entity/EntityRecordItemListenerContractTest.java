package com.hedera.mirror.importer.parser.record.entity;

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

import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.Value;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.util.Utility;

class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final Version HAPI_VERSION_0_23_0 = new Version(0, 23, 0);
    private static final String METADATA = "METADATA";
    private static final TokenID TOKEN_ID = TokenID.newBuilder().setTokenNum(903).build();

    // saves the mapping from proto ContractID to EntityId so as not to use EntityIdService to verify itself
    private Map<ContractID, EntityId> contractIds;

    @Resource
    private ContractLogRepository contractLogRepository;

    @Resource
    private ContractStateChangeRepository contractStateChangeRepository;

    @BeforeEach
    void before() {
        contractIds = new HashMap<>();
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contractCreate(boolean bytecodeSourceFileId) {
        var builder = recordItemBuilder.contractCreate();
        if (!bytecodeSourceFileId) {
            builder.transactionBody(b -> b.clearFileID().setInitcode(recordItemBuilder.bytes(1024)));
        }
        var recordItem = builder.build();
        var record = recordItem.getRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, record)
        );
    }

    @Test
    void contractCreateWithEvmAddress() {
        // no child tx, creates a single contract with evm address set
        byte[] evmAddress = domainBuilder.create2EvmAddress();
        RecordItem recordItem = recordItemBuilder.contractCreate(CONTRACT_ID)
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs()
                        .addCreatedContractIDs(CONTRACT_ID)
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(evmAddress)))
                ))
                .hapiVersion(RecordFile.HAPI_VERSION_0_23_0)
                .build();
        var record = recordItem.getRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, record)
        );
    }

    @Test
    void contractCreateWithEvmAddressAndChildCreate() {
        // given contractCreate with child contractCreate
        var parentEvmAddress = domainBuilder.create2EvmAddress();
        var parentRecordItem = recordItemBuilder.contractCreate()
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .clearStateChanges()
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(parentEvmAddress)))
                ))
                .hapiVersion(HAPI_VERSION_0_23_0)
                .build();

        var contractCreateResult = parentRecordItem.getRecord().getContractCreateResult();
        var childContractId = contractCreateResult.getCreatedContractIDsList().stream()
                .filter(c -> !c.equals(contractCreateResult.getContractID()))
                .findFirst().get();
        var childEvmAddress = domainBuilder.create2EvmAddress();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childTransactionId = parentRecordItem.getRecord().getTransactionID().toBuilder().setNonce(1);
        var childRecordItem = recordItemBuilder.contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setTransactionID(childTransactionId)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .clearStateChanges()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress)))))
                .hapiVersion(HAPI_VERSION_0_23_0)
                .build();

        // when
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        // then
        var parentTransactionBody = parentRecordItem.getTransactionBody().getContractCreateInstance();
        var childTransactionBody = childRecordItem.getTransactionBody().getContractCreateInstance();
        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertContractEntity(parentRecordItem),
                () -> assertContractEntity(childRecordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(2),
                () -> assertContractCreateResult(parentTransactionBody, parentRecordItem.getRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getRecord())
        );
    }

    @Test
    void contractCreateFailedWithResult() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(r -> r.setContractCreateResult(ContractFunctionResult.getDefaultInstance()))
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        var record = recordItem.getRecord();
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
                .record(r -> r.clearContractCreateResult())
                .build();
        var record = recordItem.getRecord();
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
        var record = recordItem.getRecord();
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
    @EnumSource(ContractIdType.class)
    void contractUpdateAllToExisting(ContractIdType contractIdType) {
        // first create the contract
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true, c -> c.obtainerId(null));
        Contract contract = setupResult.contract;

        // now update
        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.contract.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(contract.getCreatedTimestamp(), Contract::getCreatedTimestamp)
                        .returns(contract.getFileId(), Contract::getFileId) // FileId is ignored on updates by HAPI
        );
    }

    @Test
    void contractUpdateAllWithMemoToExisting() {
        // first create the contract
        EntityId contractId = EntityId.of(CONTRACT_ID);
        Contract contract = domainBuilder.contract()
                .customize(c -> c.obtainerId(null).id(contractId.getId()).num(contractId.getEntityNum()))
                .persist();

        // now update
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(contract.getCreatedTimestamp(), Contract::getCreatedTimestamp)
                        .returns(contract.getFileId(), Contract::getFileId) // FileId is ignored on updates by HAPI
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

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.contract.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(null, Contract::getCreatedTimestamp)
                        .returns(null, Contract::getFileId) // FileId is ignored on updates by HAPI
        );
    }

    @Test
    void contractUpdateAllToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.contract.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.contract.toEntityId())
        );
    }

    @Test
    void contractUpdateAllWithMemoToNew() {
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
                        .returns(null, Contract::getCreatedTimestamp)
                        .returns(null, Contract::getFileId) // FileId is ignored on updates by HAPI
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
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(contractRepository.findAll()).containsExactly(setupResult.contract)
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractDeleteToExisting(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        Contract dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.contract.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbContractEntity)
                        .isNotNull()
                        .returns(true, Contract::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Contract::getTimestampLower)
                        .returns(EntityId.of(PAYER), Contract::getObtainerId)
                        .usingRecursiveComparison()
                        .ignoringFields("deleted", "obtainerId", "timestampRange")
                        .isEqualTo(setupResult.contract)
        );
    }

    @ParameterizedTest
    @EnumSource(value = ContractIdType.class, names = {"PLAIN", "PARSABLE_EVM"})
    void contractDeleteToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work for PLAIN and PARSABLE_EVM
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);
        Contract contract = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(contract)
                        .isNotNull()
                        .returns(true, Contract::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Contract::getTimestampLower)
                        .returns(null, Contract::getAutoRenewPeriod)
                        .returns(null, Contract::getExpirationTimestamp)
                        .returns(null, Contract::getKey)
                        .returns(EntityId.of(PAYER), Contract::getObtainerId)
                        .returns(null, Contract::getProxyAccountId)

        );
    }

    @Test
    void contractDeleteToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.contract.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.contract.toEntityId())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                ContractTransactionType.DELETE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

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

        // now call
        Transaction transaction = contractCallTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = getContractTransactionRecord(transactionBody, ContractTransactionType.CALL);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(CREATED_CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractCallResult(contractCallTransactionBody, record),
                () -> assertThat(contractRepository.findAll()).contains(setupResult.contract)
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToExistingWithChildContractCreate(ContractIdType contractIdType) {
        // given contractCall with child contractCreate
        var setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);
        var parentId = setupResult.contract.toEntityId();

        var parentRecordItem = recordItemBuilder.contractCall()
                .receipt(r -> r.setContractID(CONTRACT_ID))
                .transactionBody(b -> b.setContractID(setupResult.protoContractId))
                .record(r -> r.clearContractCallResult()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)
                                .clearStateChanges()))
                .hapiVersion(HAPI_VERSION_0_23_0)
                .build();

        var childEvmAddress = domainBuilder.create2EvmAddress();
        var record = parentRecordItem.getRecord();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childContractId = record.getContractCallResult().getCreatedContractIDs(0);
        var childTransactionId = record.getTransactionID().toBuilder().setNonce(1).build();
        var childRecordItem = recordItemBuilder.contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .clearStateChanges()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress))))
                        .setTransactionID(childTransactionId))
                .hapiVersion(HAPI_VERSION_0_23_0)
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
                () -> assertTransactionAndRecord(parentRecordItem.getTransactionBody(), parentRecordItem.getRecord()),
                () -> assertTransactionAndRecord(childRecordItem.getTransactionBody(), childRecordItem.getRecord()),
                () -> assertThat(contractRepository.findAll()).contains(setupResult.contract),
                () -> assertCreatedContract(childRecordItem),
                () -> assertContractCallResult(parentTransactionBody, parentRecordItem.getRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getRecord())
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
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CREATED_CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContractCallResult(contractCallTransactionBody, record)
        );
    }

    @Test
    void contractCallTokenPrecompiles() {
        // given
        RecordItem recordItemCall = recordItemBuilder.contractCall().build();
        parseRecordItemAndCommit(recordItemCall);

        var parentConsensusTimestamp = recordItemCall.getRecord().getConsensusTimestamp();
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
        var record = recordItem.getRecord();
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

        parseRecordItemAndCommit(new RecordItem(transaction, record));

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

        parseRecordItemAndCommit(new RecordItem(transaction, record));

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
        Transaction transaction = contractCallTransaction(ContractID.newBuilder().setContractNum(-1L).build());
        var transactionBody = getTransactionBody(transaction);
        TransactionRecord record = buildTransactionRecord(recordBuilder -> {
            var contractFunctionResult = recordBuilder.getContractCallResultBuilder();
            buildContractFunctionResult(contractFunctionResult);
            contractFunctionResult.removeCreatedContractIDs(0); // Only contract create can contain parent ID
        }, transactionBody, ResponseCodeEnum.INVALID_CONTRACT_ID.getNumber());

        parseRecordItemAndCommit(new RecordItem(transaction, record));

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
        var contractCreateResult = recordItem.getRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress() ?
                DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()) : null;
        EntityId entityId = transaction.getEntityId();
        Contract contract = getEntity(entityId);
        EntityId expectedFileId = transactionBody.hasFileID() ? EntityId.of(transactionBody.getFileID()) : null;
        byte[] expectedInitcode = transactionBody.getInitcode() != ByteString.EMPTY ?
                DomainUtils.toBytes(transactionBody.getInitcode()) : null;

        assertThat(transaction)
                .isNotNull()
                .returns(transactionBody.getInitialBalance(), t -> t.getInitialBalance());

        assertThat(contract)
                .isNotNull()
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Contract::getAutoRenewPeriod)
                .returns(createdTimestamp, Contract::getCreatedTimestamp)
                .returns(false, Contract::getDeleted)
                .returns(evmAddress, Contract::getEvmAddress)
                .returns(null, Contract::getExpirationTimestamp)
                .returns(expectedFileId, Contract::getFileId)
                .returns(entityId.getId(), Contract::getId)
                .returns(expectedInitcode, Contract::getInitcode)
                .returns(adminKey, Contract::getKey)
                .returns(transactionBody.getMaxAutomaticTokenAssociations(), Contract::getMaxAutomaticTokenAssociations)
                .returns(transactionBody.getMemo(), Contract::getMemo)
                .returns(createdTimestamp, Contract::getTimestampLower)
                .returns(null, Contract::getObtainerId)
                .returns(EntityId.of(transactionBody.getProxyAccountID()), Contract::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Contract::getPublicKey)
                .returns(EntityType.CONTRACT, Contract::getType);

        if (entityProperties.getPersist().isContracts()) {
            assertCreatedContract(recordItem);
        }
    }

    private void assertCreatedContract(RecordItem recordItem) {
        var contractCreateResult = recordItem.getRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress() ?
                DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue()) : null;
        EntityId createdId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        assertThat(contractRepository.findById(createdId.getId()))
                .get()
                .returns(recordItem.getConsensusTimestamp(), Contract::getCreatedTimestamp)
                .returns(false, Contract::getDeleted)
                .returns(evmAddress, Contract::getEvmAddress)
                .returns(createdId.getId(), Contract::getId)
                .returns(recordItem.getConsensusTimestamp(), Contract::getTimestampLower)
                .returns(createdId.getEntityNum(), Contract::getNum)
                .returns(createdId.getShardNum(), Contract::getShard)
                .returns(createdId.getType(), Contract::getType);
    }

    private ObjectAssert<Contract> assertContractEntity(ContractUpdateTransactionBody expected,
                                                        Timestamp consensusTimestamp) {
        Contract contract = getTransactionEntity(consensusTimestamp);
        long updatedTimestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        var adminKey = expected.getAdminKey().toByteArray();
        var expectedMaxAutomaticTokenAssociations = expected.hasMaxAutomaticTokenAssociations() ?
                expected.getMaxAutomaticTokenAssociations().getValue() : null;

        return assertThat(contract)
                .isNotNull()
                .returns(expected.getAutoRenewPeriod().getSeconds(), Contract::getAutoRenewPeriod)
                .returns(false, Contract::getDeleted)
                .returns(DomainUtils.timeStampInNanos(expected.getExpirationTime()), Contract::getExpirationTimestamp)
                .returns(adminKey, Contract::getKey)
                .returns(expectedMaxAutomaticTokenAssociations, Contract::getMaxAutomaticTokenAssociations)
                .returns(getMemoFromContractUpdateTransactionBody(expected), Contract::getMemo)
                .returns(updatedTimestamp, Contract::getTimestampLower)
                .returns(null, Contract::getObtainerId)
                .returns(EntityId.of(expected.getProxyAccountID()), Contract::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Contract::getPublicKey)
                .returns(EntityType.CONTRACT, Contract::getType);
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
                .returns(EntityId.of(receipt.getContractID()), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        if (receipt.getStatus() == ResponseCodeEnum.SUCCESS) {
            contractResult.returns(EntityId.of(receipt.getContractID()), ContractResult::getContractId);
        }

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult,
                result.getStateChangesList());
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
                .returns(contractId, ContractResult::getContractId)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult,
                result.getStateChangesList());
    }

    private void assertContractResult(long consensusTimestamp, ContractFunctionResult result,
                                      List<ContractLoginfo> logInfoList,
                                      ObjectAssert<ContractResult> contractResult,
                                      List<com.hederahashgraph.api.proto.java.ContractStateChange> stateChanges) {
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

        int count = 0;
        var contractStateChanges = assertThat(contractStateChangeRepository.findAll());

        for (var contractStateChangeInfo : stateChanges) {
            EntityId contractId = EntityId.of(contractStateChangeInfo.getContractID());
            for (var storageChange : contractStateChangeInfo.getStorageChangesList()) {
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten() ? storageChange.getValueWritten().getValue()
                        .toByteArray() : null;

                contractStateChanges.filteredOn(c -> c.getConsensusTimestamp() == consensusTimestamp
                        && c.getContractId() == contractId.getId() && Arrays.equals(c.getSlot(), slot))
                        .hasSize(1)
                        .first()
                        .returns(storageChange.getValueRead().toByteArray(), ContractStateChange::getValueRead)
                        .returns(valueWritten, ContractStateChange::getValueWritten);
                ++count;
            }
        }

        contractStateChanges.hasSize(count);
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
        ContractFunctionResult result = record.getContractCallResult();

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(EntityId.of(transactionBody.getContractID()), ContractResult::getContractId)
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
        });
    }

    private Transaction contractDeleteTransaction(ContractID contractId) {
        return buildTransaction(builder -> {
            ContractDeleteTransactionBody.Builder contractDelete = builder.getContractDeleteInstanceBuilder();
            contractDelete.setContractID(contractId);
            contractDelete.setTransferAccountID(PAYER);
        });
    }

    private Transaction contractDeleteTransaction() {
        return contractDeleteTransaction(CONTRACT_ID);
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

    private Transaction tokenAssociateTransaction() {
        return buildTransaction(builder -> builder.getTokenAssociateBuilder()
                .setAccount(PAYER2)
                .addAllTokens(List.of(TOKEN_ID)));
    }

    private Transaction tokenDissociateTransaction() {
        return buildTransaction(builder -> builder.getTokenDissociateBuilder()
                .setAccount(PAYER2)
                .addAllTokens(List.of(TOKEN_ID)));
    }

    private Transaction cryptoTransferTransaction() {
        return buildTransaction(TransactionBody.Builder::getCryptoTransferBuilder);
    }

    private Transaction tokenSupplyTransaction(TokenType tokenType, boolean mint) {
        var serialNumbers = List.of(1L, 2L, 3L);
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> {
                builder.getTokenMintBuilder()
                        .setToken(TOKEN_ID);
                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenMintBuilder().setAmount(10);
                } else {
                    builder.getTokenMintBuilder().addAllMetadata(Collections
                            .nCopies(serialNumbers.size(), ByteString.copyFromUtf8(METADATA)));
                }
            });
        } else {
            transaction = buildTransaction(builder -> {
                builder.getTokenBurnBuilder()
                        .setToken(TOKEN_ID);
                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenBurnBuilder().setAmount(10);
                } else {
                    builder.getTokenBurnBuilder()
                            .addAllSerialNumbers(serialNumbers);
                }
            });
        }

        return transaction;
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
                return domainBuilder.create2EvmAddress();
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
                    return EntityId.of(shard, realm, num, EntityType.CONTRACT);
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
                                      boolean cache, Consumer<Contract.ContractBuilder> customizer) {
        EntityId entityId = EntityId.of(contractId);
        byte[] evmAddress = getEvmAddress(contractIdType, entityId);
        ContractID protoContractId = getContractId(CONTRACT_ID, evmAddress);
        var builder = domainBuilder.contract()
                .customize(c -> c.evmAddress(evmAddress).id(entityId.getId()).num(entityId.getEntityNum()));
        if (customizer != null) {
            builder.customize(customizer);
        }
        Contract contract = persist ? builder.persist() : builder.get();
        if (cache) {
            contractIds.put(protoContractId, entityId);
        }
        return new SetupResult(contract, protoContractId);
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
        Contract contract;
        ContractID protoContractId;
    }
}
