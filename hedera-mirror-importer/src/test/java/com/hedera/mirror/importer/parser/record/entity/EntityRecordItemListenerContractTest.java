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

import static com.hedera.mirror.importer.util.Utility.toBytes;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
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
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.ContractLog;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.transactionhandler.EntityOperation;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.util.Utility;

class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final ContractID CONTRACT_ID = ContractID.newBuilder().setContractNum(1001).build();
    private static final ContractID CREATED_CONTRACT_ID = ContractID.newBuilder().setContractNum(1002).build();
    private static final FileID FILE_ID = FileID.newBuilder().setFileNum(1003).build();

    @Resource
    private ContractLogRepository contractLogRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void contractCreate() {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(CREATED_CONTRACT_ID), EntityId.of(PROXY),
                        EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertContractCreateResult(contractCreateTransactionBody, record)
        );
    }

    @Test
    void contractCreateFailedWithResult() {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        // Clear receipt.contractID since transaction is failure.
        TransactionRecord.Builder recordBuilder = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION).toBuilder();
        TransactionRecord record = recordBuilder
                .setReceipt(recordBuilder.getReceiptBuilder().clearContractID())
                .build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateFailedWithoutResult() {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        // Clear receipt.contractID since transaction is failure.
        TransactionRecord.Builder recordBuilder =
                createOrUpdateRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                        .toBuilder()
                        .clearContractCreateResult();
        TransactionRecord record = recordBuilder
                .setReceipt(recordBuilder.getReceiptBuilder().clearContractID())
                .build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateDoNotPersist() {
        entityProperties.getPersist().setContracts(false);

        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(recordItem)
                , () -> assertFalse(getContractResult(record.getConsensusTimestamp()).isPresent())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contractUpdateAllToExisting(boolean updateMemoWrapperOrMemo) throws Exception {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(contractCreateTransaction);
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction(updateMemoWrapperOrMemo);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(CREATED_CONTRACT_ID), EntityId.of(PROXY),
                        EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY), EntityId.of(PROXY_UPDATE))
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contractUpdateAllToNew(boolean updateMemoWrapperOrMemo) {
        Transaction transaction = contractUpdateAllTransaction(updateMemoWrapperOrMemo);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY_UPDATE), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void contractUpdateAllToExistingInvalidTransaction() {
        EntityId contractId = EntityId.of(CONTRACT_ID);
        Contract contract = domainBuilder.contract()
                .customize(c -> c.id(contractId.getId()).num(contractId.getEntityNum()))
                .persist();

        Transaction transaction = contractUpdateAllTransaction(true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(contractId, EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractTransaction(transactionBody, record, false),
                () -> assertThat(contractRepository.findAll()).containsExactly(contract)
        );
    }

    @Test
    void contractDeleteToExisting() {
        EntityId contractId = EntityId.of(CONTRACT_ID);
        Contract contract = domainBuilder.contract()
                .customize(c -> c.id(contractId.getId()).num(contractId.getEntityNum()))
                .persist();

        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        Contract dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(contractId, EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractTransaction(transactionBody, record, true),
                () -> assertThat(dbContractEntity)
                        .isNotNull()
                        .returns(true, Contract::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Contract::getModifiedTimestamp)
                        .usingRecursiveComparison()
                        .ignoringFields("deleted", "timestampRange")
                        .isEqualTo(contract)
        );
    }

    @Test
    void contractDeleteToNew() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PAYER), EntityId.of(NODE),
                        EntityId.of(TREASURY)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractTransaction(transactionBody, record, true),
                () -> assertContractEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
                , () -> assertThat(transactionBody.getContractDeleteInstance().getContractID()).isNotNull()
        );
    }

    @Test
    void contractCallToExisting() {
        EntityId parentId = EntityId.of(CONTRACT_ID);
        Contract parent = domainBuilder.contract()
                .customize(c -> c.id(parentId.getId()).num(parentId.getEntityNum()))
                .persist();

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(CREATED_CONTRACT_ID), EntityId.of(PAYER),
                        EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractTransaction(transactionBody, record, false),
                () -> assertContractCallResult(contractCallTransactionBody, record),
                () -> assertCreatedContract(parent, recordItem)
        );
    }

    @Test
    void contractCallToNew() {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();
        RecordItem recordItem = new RecordItem(transaction, record);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(CREATED_CONTRACT_ID), EntityId.of(PAYER),
                        EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertContractTransaction(transactionBody, record, false),
                () -> assertContractCallResult(contractCallTransactionBody, record)
        );
    }

    @Test
    void contractCallFailedWithResult() {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
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
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    private void assertContractTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Contract actualContract = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContract(record.getReceipt().getContractID(), actualContract),
                () -> assertEntity(actualContract));

        TransactionTypeEnum transactionType = TransactionTypeEnum.of(transactionBody.getDataCase().getNumber());
        if (transactionType.getEntityOperation() != EntityOperation.NONE) {
            assertThat(actualContract.getDeleted()).isEqualTo(deleted);
        }
    }

    private void assertFailedContractCreate(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(transactionBody.getContractCreateInstance().getInitialBalance(),
                        dbTransaction.getInitialBalance()));
    }

    private void assertFailedContractCallTransaction(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertThat(dbTransaction.getEntityId()).isNotNull(),
                () -> assertEquals(EntityId.of(transactionBody.getContractCall().getContractID()),
                        dbTransaction.getEntityId()));
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
                .returns(recordItem.getTransactionType(), t -> t.getType())
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
                .returns(null, Contract::getParentId)
                .returns(Utility.convertSimpleKeyToHex(adminKey), Contract::getPublicKey)
                .returns(EntityType.CONTRACT, Contract::getType)
                .extracting(Contract::getProxyAccountId)
                .isEqualTo(EntityId.of(transactionBody.getProxyAccountID()))
                .extracting(this::getEntity)
                .isNotNull();

        if (entityProperties.getPersist().isContracts()) {
            assertCreatedContract(contract, recordItem);
        }
    }

    private void assertCreatedContract(Contract parent, RecordItem recordItem) {
        EntityId createdId = EntityId.of(CREATED_CONTRACT_ID);
        assertThat(contractRepository.findById(createdId.getId()))
                .get()
                .returns(recordItem.getConsensusTimestamp(), Contract::getCreatedTimestamp)
                .returns(createdId.getId(), Contract::getId)
                .returns(recordItem.getConsensusTimestamp(), Contract::getModifiedTimestamp)
                .returns(createdId.getEntityNum(), Contract::getNum)
                .returns(parent.toEntityId(), Contract::getParentId)
                .usingRecursiveComparison()
                .ignoringFields("createdTimestamp", "id", "num", "parentId", "timestampRange")
                .isEqualTo(parent);
    }

    private void assertContractEntity(ContractUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Contract actualContract = getTransactionEntity(consensusTimestamp);
        Entity actualProxyAccount = getEntity(actualContract.getProxyAccountId());
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualContract.getAutoRenewPeriod()),
                () -> assertArrayEquals(expected.getAdminKey().toByteArray(), actualContract.getKey()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccount),
                () -> assertEquals(getMemoFromContractUpdateTransactionBody(expected), actualContract.getMemo()),
                () -> assertEquals(
                        Utility.timeStampInNanos(expected.getExpirationTime()), actualContract
                                .getExpirationTimestamp()));
    }

    private void assertContractEntityHasNullFields(Timestamp consensusTimestamp) {
        Contract actualContract = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualContract.getKey()),
                () -> assertNull(actualContract.getExpirationTimestamp()),
                () -> assertNull(actualContract.getAutoRenewPeriod()),
                () -> assertNull(actualContract.getProxyAccountId()));
    }

    private void assertContractCreateResult(ContractCreateTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = Utility.timestampInNanosMax(record.getConsensusTimestamp());
        ContractFunctionResult result = record.getContractCreateResult();
        ContractLoginfo logInfo = result.getLogInfo(0);

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(EntityId.of(record.getReceipt().getContractID()), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertContractResult(consensusTimestamp, result, logInfo, contractResult);
    }

    private void assertContractCallResult(ContractCallTransactionBody transactionBody, TransactionRecord record) {
        long consensusTimestamp = Utility.timestampInNanosMax(record.getConsensusTimestamp());
        ContractFunctionResult result = record.getContractCallResult();
        ContractLoginfo logInfo = result.getLogInfo(0);

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(EntityId.of(transactionBody.getContractID()), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertContractResult(consensusTimestamp, result, logInfo, contractResult);
    }

    private void assertContractResult(long consensusTimestamp, ContractFunctionResult result, ContractLoginfo logInfo
            , ObjectAssert<ContractResult> contractResult) {
        contractResult
                .returns(result.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(result.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(List.of(CONTRACT_ID.getContractNum(), CREATED_CONTRACT_ID.getContractNum()),
                        ContractResult::getCreatedContractIds)
                .returns(result.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(result.toByteArray(), ContractResult::getFunctionResult)
                .returns(result.getGasUsed(), ContractResult::getGasUsed);

        assertThat(contractLogRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp() == consensusTimestamp)
                .hasSize(1)
                .first()
                .returns(result.getBloom().toByteArray(), ContractLog::getBloom)
                .returns(consensusTimestamp, ContractLog::getConsensusTimestamp)
                .returns(EntityId.of(logInfo.getContractID()), ContractLog::getContractId)
                .returns(logInfo.getData().toByteArray(), ContractLog::getData)
                .returns(0, ContractLog::getIndex)
                .returns(Hex.encodeHexString(logInfo.getTopic(0).toByteArray()), ContractLog::getTopic0)
                .returns(Hex.encodeHexString(logInfo.getTopic(1).toByteArray()), ContractLog::getTopic1)
                .returns(Hex.encodeHexString(logInfo.getTopic(2).toByteArray()), ContractLog::getTopic2)
                .returns(Hex.encodeHexString(logInfo.getTopic(3).toByteArray()), ContractLog::getTopic3);
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
            buildContractFunctionResult(recordBuilder.getContractCallResultBuilder());
        }, transactionBody, status.getNumber());
    }

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
                .setContractID(CREATED_CONTRACT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .addTopic(ByteString.copyFromUtf8("Topic0"))
                .addTopic(ByteString.copyFromUtf8("Topic1"))
                .addTopic(ByteString.copyFromUtf8("Topic2"))
                .addTopic(ByteString.copyFromUtf8("Topic3")).build());
    }

    private Transaction contractCreateTransaction() {
        return buildTransaction(builder -> {
            ContractCreateTransactionBody.Builder contractCreate = builder.getContractCreateInstanceBuilder();
            contractCreate.setAdminKey(keyFromString(KEY));
            contractCreate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(100).build());
            contractCreate.setConstructorParameters(ByteString.copyFromUtf8("Constructor Parameters"));
            contractCreate.setFileID(FILE_ID);
            contractCreate.setGas(10000L);
            contractCreate.setInitialBalance(20000L);
            contractCreate.setMemo("Contract Memo");
            contractCreate.setNewRealmAdminKey(keyFromString(KEY2));
            contractCreate.setProxyAccountID(PROXY);
            contractCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
            contractCreate.setShardID(ShardID.newBuilder().setShardNum(0));
        });
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

    private Transaction contractDeleteTransaction() {
        return buildTransaction(builder -> {
            ContractDeleteTransactionBody.Builder contractDelete = builder.getContractDeleteInstanceBuilder();
            contractDelete.setContractID(CONTRACT_ID);
        });
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
        return contractResultRepository.findById(Utility.timeStampInNanos(consensusTimestamp));
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
}
