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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final ContractID CONTRACT_ID =
            ContractID.newBuilder().setShardNum(0).setRealmNum(0).setContractNum(1001).build();
    private static final FileID FILE_ID = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1002).build();

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

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractEntity(contractCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertContractCreateResult(contractCreateTransactionBody, record)
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
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
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
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateDoNotPersist() {
        entityProperties.getPersist().setContracts(false);

        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractCreateTransactionBody, record.getConsensusTimestamp())
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
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY), EntityId.of(PROXY_UPDATE))
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
    void contractUpdateAllToNew(boolean updateMemoWrapperOrMemo) throws Exception {
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
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void contractUpdateAllToExistingInvalidTransaction() {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(contractCreateTransaction);
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);
        ContractCreateTransactionBody contractCreateTransactionBody = createTransactionBody
                .getContractCreateInstance();

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction(true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertContractEntity(contractCreateTransactionBody, recordCreate.getConsensusTimestamp())
        );
    }

    @Test
    void contractDeleteToExisting() {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(contractCreateTransaction);
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                , () -> assertContractTransaction(transactionBody, record, true)

                // Additional entity checks
                , () -> assertNotNull(dbContractEntity.getKey())
                , () -> assertNull(dbContractEntity.getExpirationTimestamp())
                , () -> assertNotNull(dbContractEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbContractEntity.getProxyAccountId())
        );
    }

    @Test
    void contractDeleteToNew() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, true)
                , () -> assertContractEntityHasNullFields(record.getConsensusTimestamp())
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
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
                , () -> assertThat(transactionBody.getContractDeleteInstance().getContractID()).isNotNull()
        );
    }

    @Test
    void contractCallToExisting() {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(contractCreateTransaction);
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY), EntityId.of(PROXY))
                , () -> assertEquals(2, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractCallResult(contractCallTransactionBody, record)
        );
    }

    @Test
    void contractCallToNew() {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId.of(CONTRACT_ID), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractCallResult(contractCallTransactionBody, record)
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
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
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
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
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
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
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
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    private void assertContractTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Entity actualContract = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContract(record.getReceipt().getContractID(), actualContract),
                () -> assertEntity(actualContract));
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

    private void assertContractEntity(ContractCreateTransactionBody expected, Timestamp consensusTimestamp) {
        var dbTransaction = getDbTransaction(consensusTimestamp);
        Entity actualContract = getEntity(dbTransaction.getEntityId());
        Entity actualProxyAccount = getEntity(actualContract.getProxyAccountId());
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualContract.getAutoRenewPeriod()),
                () -> assertArrayEquals(expected.getAdminKey().toByteArray(), actualContract.getKey()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccount),
                () -> assertEquals(expected.getMemo(), actualContract.getMemo()),
                () -> assertEquals(expected.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertNull(actualContract.getExpirationTimestamp()));
    }

    private void assertContractEntity(ContractUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualContract = getTransactionEntity(consensusTimestamp);
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
        Entity actualContract = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualContract.getKey()),
                () -> assertNull(actualContract.getExpirationTimestamp()),
                () -> assertNull(actualContract.getAutoRenewPeriod()),
                () -> assertNull(actualContract.getProxyAccountId()));
    }

    private void assertContractCreateResult(ContractCreateTransactionBody expected, TransactionRecord record) {
        ContractResult contractResult = getContractResult(record.getConsensusTimestamp()).get();
        assertAll(
                () -> assertArrayEquals(
                        expected.getConstructorParameters().toByteArray(), contractResult.getFunctionParameters()),
                () -> assertEquals(expected.getGas(), contractResult.getGasSupplied()),
                () -> assertArrayEquals(record.getContractCreateResult().toByteArray(), contractResult.getCallResult()),
                () -> assertEquals(record.getContractCreateResult().getGasUsed(), contractResult.getGasUsed()));
    }

    private void assertContractCallResult(ContractCallTransactionBody expected, TransactionRecord record) {
        ContractResult contractResult = getContractResult(record.getConsensusTimestamp()).get();
        assertAll(
                () -> assertArrayEquals(record.getContractCallResult().toByteArray(), contractResult.getCallResult()),
                () -> assertEquals(record.getContractCallResult().getGasUsed(), contractResult.getGasUsed()),
                () -> assertArrayEquals(
                        expected.getFunctionParameters().toByteArray(), contractResult.getFunctionParameters()),
                () -> assertEquals(expected.getGas(), contractResult.getGasSupplied()));
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
        builder.setErrorMessage("call error message");
        builder.setGasUsed(30);
        builder.addLogInfo(ContractLoginfo.newBuilder().addTopic(ByteString.copyFromUtf8("Topic")).build());
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
