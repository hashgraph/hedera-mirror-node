package com.hedera.mirror.importer.parser.record.entity;

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

import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_ENTITY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
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
import com.hederahashgraph.api.proto.java.StorageChange;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import lombok.Value;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.util.Utility;

@SuppressWarnings("deprecation")
class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final ContractID CONTRACT_ID = ContractID.newBuilder().setContractNum(1001).build();
    private static final ContractID CREATED_CONTRACT_ID = ContractID.newBuilder().setContractNum(1002).build();

    // saves the mapping from proto ContractID to EntityId so as not to use EntityIdService to verify itself
    private Map<ContractID, EntityId> contractIds;

    @Resource
    private ContractLogRepository contractLogRepository;

    @Resource
    private ContractStateChangeRepository contractStateChangeRepository;

    @Resource
    private RecordItemBuilder recordItemBuilder;

    @Resource(name = CACHE_MANAGER_ENTITY_ID)
    private CacheManager cacheManager;

    @BeforeEach
    void before() {
        cacheManager.getCacheNames().stream().map(cacheManager::getCache).forEach(Cache::clear);
        contractIds = new HashMap<>();
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void contractCreate() {
        RecordItem recordItem = recordItemBuilder.contractCreate().build();
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
                () -> assertContractCreateResult(transactionBody, record)
        );
    }

    @Test
    void contractCreateWithEvmAddress() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .setEvmAddress(BytesValue.of(ByteString.copyFrom(domainBuilder.create2EvmAddress())))
                ))
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
                () -> assertContractCreateResult(transactionBody, record)
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
        EntityId contractId = EntityId.of(CONTRACT_ID);
        SetupResult setupResult = setupContract(contractId, contractIdType, true, true, c -> c.obtainerId(null));
        Contract contract = setupResult.contract;

        // now update
        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(contractId),
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
        TransactionRecord record = createOrUpdateRecord(transactionBody);
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
        EntityId contractId = EntityId.of(CONTRACT_ID);
        SetupResult setupResult = setupContract(contractId, contractIdType, false, false, c -> c.obtainerId(null));

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(contractId),
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
        EntityId contractId = EntityId.of(CONTRACT_ID);
        SetupResult setupResult = setupContract(contractId, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                this::assertEntities,
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    @Test
    void contractUpdateAllWithMemoToNew() {
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
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
        SetupResult setupResult = setupContract(EntityId.of(CONTRACT_ID), contractIdType, true, true);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);
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
        EntityId contractId = EntityId.of(CONTRACT_ID);
        SetupResult setupResult = setupContract(contractId, contractIdType, true, true);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        Contract dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(contractId),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbContractEntity)
                        .isNotNull()
                        .returns(true, Contract::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Contract::getModifiedTimestamp)
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
        SetupResult setupResult = setupContract(EntityId.of(CONTRACT_ID), contractIdType, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
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
                        .returns(recordItem.getConsensusTimestamp(), Contract::getModifiedTimestamp)
                        .returns(null, Contract::getAutoRenewPeriod)
                        .returns(null, Contract::getExpirationTimestamp)
                        .returns(null, Contract::getKey)
                        .returns(EntityId.of(PAYER), Contract::getObtainerId)
                        .returns(null, Contract::getProxyAccountId)

        );
    }

    @Test
    void contractDeleteToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(EntityId.of(CONTRACT_ID), ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                this::assertEntities,
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

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
        EntityId parentId = EntityId.of(CONTRACT_ID);
        SetupResult setupResult = setupContract(parentId, contractIdType, true, true);

        // now call
        Transaction transaction = contractCallTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
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
                () -> assertThat(contractRepository.findById(parentId.getId())).get().isEqualTo(setupResult.contract) // No change
        );
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work. Note for the create2 evm address,
        // ContractCallTransactionHandler will get the correct plain contractId from the transaction record instead
        EntityId contractId = EntityId.of(CONTRACT_ID);
        // only cache the create2 evm address to verify it later
        SetupResult setupResult = setupContract(contractId, contractIdType, false,
                contractIdType == ContractIdType.CREATE2_EVM);

        Transaction transaction = contractCallTransaction(setupResult.protoContractId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
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
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
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
        TransactionRecord record = callRecord(transactionBody);

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
    void cryptoTransferBadContractId() {
        Transaction transaction = contractCallTransaction(ContractID.newBuilder().setContractNum(-1L).build());
        var transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.INVALID_CONTRACT_ID);

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
        EntityId entityId = transaction.getEntityId();
        Contract contract = getEntity(entityId);

        assertThat(transaction)
                .isNotNull()
                .returns(transactionBody.getInitialBalance(), t -> t.getInitialBalance());

        assertThat(contract)
                .isNotNull()
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Contract::getAutoRenewPeriod)
                .returns(createdTimestamp, Contract::getCreatedTimestamp)
                .returns(false, Contract::getDeleted)
                .returns(null, Contract::getExpirationTimestamp)
                .returns(entityId.getId(), Contract::getId)
                .returns(EntityId.of(transactionBody.getFileID()), Contract::getFileId)
                .returns(adminKey, Contract::getKey)
                .returns(transactionBody.getMemo(), Contract::getMemo)
                .returns(createdTimestamp, Contract::getModifiedTimestamp)
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
                .returns(recordItem.getConsensusTimestamp(), Contract::getModifiedTimestamp)
                .returns(createdId.getEntityNum(), Contract::getNum)
                .returns(createdId.getShardNum(), Contract::getShard)
                .returns(createdId.getType(), Contract::getType);
    }

    private ObjectAssert<Contract> assertContractEntity(ContractUpdateTransactionBody expected,
                                                        Timestamp consensusTimestamp) {
        Contract contract = getTransactionEntity(consensusTimestamp);
        long updatedTimestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        var adminKey = expected.getAdminKey().toByteArray();

        return assertThat(contract)
                .isNotNull()
                .returns(expected.getAutoRenewPeriod().getSeconds(), Contract::getAutoRenewPeriod)
                .returns(false, Contract::getDeleted)
                .returns(DomainUtils.timeStampInNanos(expected.getExpirationTime()), Contract::getExpirationTimestamp)
                .returns(adminKey, Contract::getKey)
                .returns(getMemoFromContractUpdateTransactionBody(expected), Contract::getMemo)
                .returns(updatedTimestamp, Contract::getModifiedTimestamp)
                .returns(null, Contract::getObtainerId)
                .returns(EntityId.of(expected.getProxyAccountID()), Contract::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Contract::getPublicKey)
                .returns(EntityType.CONTRACT, Contract::getType);
    }

    private void assertContractCreateResult(ContractCreateTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
        ContractFunctionResult result = record.getContractCreateResult();

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(EntityId.of(record.getReceipt().getContractID()), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        var status = record.getReceipt().getStatus();
        if (status == ResponseCodeEnum.SUCCESS) {
            contractResult
                    .returns(EntityId.of(record.getReceipt().getContractID()), ContractResult::getContractId);
        }

        assertContractResult(consensusTimestamp, record.getReceipt(), result, result.getLogInfoList(), contractResult,
                result.getStateChangesList());
    }

    private void assertContractCallResult(ContractCallTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
        ContractFunctionResult result = record.getContractCallResult();

        // get the corresponding entity id from the local cache, fall back to parseContractId if not found.
        ContractID protoContractId = transactionBody.getContractID();
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

        assertContractResult(consensusTimestamp, record.getReceipt(), result, result.getLogInfoList(), contractResult,
                result.getStateChangesList());
    }

    private void assertContractResult(long consensusTimestamp, TransactionReceipt receipt, ContractFunctionResult result,
                                      List<ContractLoginfo> logInfoList,
                                      ObjectAssert<ContractResult> contractResult,
                                      List<com.hederahashgraph.api.proto.java.ContractStateChange> stageChangeList) {
        List<Long> createdContractIds = result.getCreatedContractIDsList()
                .stream()
                .map(ContractID::getContractNum)
                .collect(Collectors.toList());
        if (result.hasEvmAddress()) {
            createdContractIds.add(receipt.getContractID().getContractNum());
        }

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

        for (var contractStateChangeInfo : stageChangeList) {
            EntityId contractId = EntityId.of(contractStateChangeInfo.getContractID());
            for (int j = 0; j < contractStateChangeInfo.getStorageChangesCount(); ++j) {
                StorageChange storageChange = contractStateChangeInfo.getStorageChanges(j);
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten() ? storageChange.getValueWritten().getValue()
                        .toByteArray() : null;

                ContractStateChange.Id id = new ContractStateChange.Id();
                id.setConsensusTimestamp(consensusTimestamp);
                id.setContractId(contractId);
                id.setSlot(slot);

                assertThat(contractStateChangeRepository.findById(id))
                        .isPresent()
                        .get()
                        .returns(consensusTimestamp, ContractStateChange::getConsensusTimestamp)
                        .returns(contractId, ContractStateChange::getContractId)
                        .returns(slot, ContractStateChange::getSlot)
                        .returns(storageChange.getValueRead().toByteArray(), ContractStateChange::getValueRead)
                        .returns(valueWritten, ContractStateChange::getValueWritten);
            }
        }
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

    private TransactionRecord createOrUpdateRecord(TransactionBody transactionBody) {
        return createOrUpdateRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord createOrUpdateRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return buildTransactionRecord(recordBuilder -> {
            recordBuilder.getReceiptBuilder().setContractID(CONTRACT_ID);
            buildContractFunctionResult(recordBuilder.getContractCreateResultBuilder());
        }, transactionBody, status.getNumber());
    }

    private TransactionRecord callRecord(TransactionBody transactionBody) {
        return callRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord callRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return buildTransactionRecord(recordBuilder -> {
            recordBuilder.getReceiptBuilder().setContractID(CONTRACT_ID);
            var contractFunctionResult = recordBuilder.getContractCallResultBuilder();
            buildContractFunctionResult(contractFunctionResult);
            contractFunctionResult.removeCreatedContractIDs(0); // Only contract create can contain parent ID
        }, transactionBody, status.getNumber());
    }

    @SneakyThrows
    private void buildContractFunctionResult(ContractFunctionResult.Builder builder) {
        builder.setBloom(ByteString.copyFromUtf8("bloom"));
        builder.setContractCallResult(ByteString.copyFromUtf8("call result"));
        builder.setContractID(CONTRACT_ID);
        builder.addCreatedContractIDs(CONTRACT_ID);
        builder.addCreatedContractIDs(CREATED_CONTRACT_ID);
        builder.setErrorMessage("call error message");
        builder.setGasUsed(30);
        builder.addLogInfo(ContractLoginfo.newBuilder()
                .setBloom(ByteString.copyFromUtf8("bloom"))
                .setContractID(CONTRACT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .addTopic(ByteString.copyFromUtf8("Topic0"))
                .addTopic(ByteString.copyFromUtf8("Topic1"))
                .addTopic(ByteString.copyFromUtf8("Topic2"))
                .addTopic(ByteString.copyFromUtf8("Topic3")).build());
        builder.addLogInfo(ContractLoginfo.newBuilder()
                .setBloom(ByteString.copyFromUtf8("bloom"))
                .setContractID(CREATED_CONTRACT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .addTopic(ByteString.copyFromUtf8("Topic0"))
                .addTopic(ByteString.copyFromUtf8("Topic1"))
                .addTopic(ByteString.copyFromUtf8("Topic2"))
                .addTopic(ByteString.copyFromUtf8("Topic3")).build());
        // 3 state changes, no value written, valid value written and zero value written
        builder.addStateChanges(com.hederahashgraph.api.proto.java.ContractStateChange.newBuilder()
                .setContractID(CONTRACT_ID)
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(ByteString
                                .copyFromUtf8("0x000000000000000000"))
                        .setValueRead(ByteString
                                .copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                        .build())
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(ByteString
                                .copyFromUtf8("0x000000000000000001"))
                        .setValueRead(ByteString
                                .copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                        .setValueWritten(BytesValue.of(ByteString
                                .copyFromUtf8("0x000000000000000000000000000000000000000000c2a8c408d0e29d623347c5")))
                        .build())
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(ByteString
                                .copyFromUtf8("0x00000000000000002"))
                        .setValueRead(ByteString
                                .copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                        .setValueWritten(BytesValue.of(ByteString.copyFromUtf8("0")))
                        .build())
                .build());
    }

    private Transaction contractUpdateAllTransaction(boolean setMemoWrapperOrMemo) {
        return buildTransaction(builder -> {
            ContractUpdateTransactionBody.Builder contractUpdate = builder.getContractUpdateInstanceBuilder();
            contractUpdate.setAdminKey(keyFromString(KEY));
            contractUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(400).build());
            contractUpdate.setContractID(CONTRACT_ID);
            contractUpdate.setExpirationTime(Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
            contractUpdate.setFileID(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());
            if (setMemoWrapperOrMemo) {
                contractUpdate.setMemoWrapper(StringValue.of("contract update memo"));
            } else {
                contractUpdate.setMemo("contract update memo");
            }
            contractUpdate.setProxyAccountID(PROXY_UPDATE);
        });
    }

    private Transaction contractUpdateAllTransaction(ContractID contractId, boolean setMemoWrapperOrMemo) {
        return buildTransaction(builder -> {
            ContractUpdateTransactionBody.Builder contractUpdate = builder.getContractUpdateInstanceBuilder();
            contractUpdate.setAdminKey(keyFromString(KEY));
            contractUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(400).build());
            contractUpdate.setContractID(contractId);
            contractUpdate.setExpirationTime(Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
            contractUpdate.setFileID(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());
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

    private SetupResult setupContract(EntityId contractId, ContractIdType contractIdType, boolean persist,
                                      boolean cache) {
        return setupContract(contractId, contractIdType, persist, cache, null);
    }

    private SetupResult setupContract(EntityId contractId, ContractIdType contractIdType, boolean persist,
                                      boolean cache, Consumer<Contract.ContractBuilder> customizer) {
        byte[] evmAddress = getEvmAddress(contractIdType, contractId);
        ContractID protoContractId = getContractId(CONTRACT_ID, evmAddress);
        var builder = domainBuilder.contract()
                .customize(c -> c.evmAddress(evmAddress).id(contractId.getId()).num(contractId.getEntityNum()));
        if (customizer != null) {
            builder.customize(customizer);
        }
        Contract contract = persist ? builder.persist() : builder.get();
        if (cache) {
            contractIds.put(protoContractId, contractId);
        }
        return new SetupResult(contract, protoContractId);
    }

    enum ContractIdType {
        PLAIN,
        PARSABLE_EVM,
        CREATE2_EVM,
    }

    @Value
    private static class SetupResult {
        Contract contract;
        ContractID protoContractId;
    }
}
