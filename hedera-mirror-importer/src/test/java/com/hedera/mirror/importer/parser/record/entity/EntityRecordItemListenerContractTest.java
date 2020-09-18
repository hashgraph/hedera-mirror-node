package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
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
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final ContractID contractId =
            ContractID.newBuilder().setShardNum(0).setRealmNum(0).setContractNum(1001).build();
    private static final FileID fileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1002).build();

    @BeforeEach
    void before() throws Exception {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void contractCreate() throws Exception {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractEntity(contractCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertContractCreateResult(contractCreateTransactionBody, record)
        );
    }

    @Test
    void contractCreateFailedWithResult() throws Exception {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        // Clear receipt.contractID since transaction is failure.
        TransactionRecord.Builder recordBuilder = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION).toBuilder();
        TransactionRecord record = recordBuilder
                .setReceipt(recordBuilder.getReceiptBuilder().clearContractID())
                .build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(3, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateFailedWithoutResult() throws Exception {
        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
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
                () -> assertEquals(3, entityRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertFailedContractCreate(transactionBody, record)
        );
    }

    @Test
    void contractCreateDoNotPersist() throws Exception {
        entityProperties.getPersist().setContracts(false);

        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertFalse(getContractResult(record.getConsensusTimestamp()).isPresent())
        );
    }

    @Test
    void contractUpdateAllToExisting() throws Exception {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody
                .parseFrom(SignedTransaction.parseFrom(contractCreateTransaction.getSignedTransactionBytes())
                        .getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void contractUpdateAllToNew() throws Exception {
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntity(contractUpdateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void contractUpdateAllToExistingInvalidTransaction() throws Exception {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(contractCreateTransaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);
        ContractCreateTransactionBody contractCreateTransactionBody = createTransactionBody
                .getContractCreateInstance();

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities actualContract = getTransactionEntity(record.getConsensusTimestamp());
        Entities actualProxyAccount = getEntity(actualContract.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                // Additional entity checks
                , () -> assertEquals(contractCreateTransactionBody.getAutoRenewPeriod().getSeconds(),
                        actualContract.getAutoRenewPeriod())
                , () -> assertArrayEquals(contractCreateTransactionBody.getAdminKey().toByteArray(),
                        actualContract.getKey())
                , () -> assertAccount(contractCreateTransactionBody.getProxyAccountID(), actualProxyAccount)
                , () -> assertFalse(actualContract.isDeleted())
                , () -> assertNull(actualContract.getExpiryTimeNs())
        );
    }

    @Test
    void contractDeleteToExisting() throws Exception {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(contractCreateTransaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbContractEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                , () -> assertContractTransaction(transactionBody, record, true)

                // Additional entity checks
                , () -> assertNotNull(dbContractEntity.getKey())
                , () -> assertNull(dbContractEntity.getExpiryTimeNs())
                , () -> assertNotNull(dbContractEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbContractEntity.getProxyAccountId())
        );
    }

    @Test
    void contractDeleteToNew() throws Exception {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, true)
                , () -> assertContractEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() throws Exception {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractEntityHasNullFields(record.getConsensusTimestamp())
        );
    }

    @Test
    void contractCallToExisting() throws Exception {
        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(contractCreateTransaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(2, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractCallResult(contractCallTransactionBody, record)
        );
    }

    @Test
    void contractCallToNew() throws Exception {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
                , () -> assertContractCallResult(contractCallTransactionBody, record)
        );
    }

    @Test
    void contractCallFailedWithResult() throws Exception {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(4, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertContractTransaction(transactionBody, record, false)
        );
    }

    @Test
    void contractCallFailedWithoutResult() throws Exception {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .toBuilder().clearContractCallResult().build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(4, entityRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertContractTransaction(transactionBody, record, false)
        );
    }

    @Test
    void contractCallDoNotPersist() throws Exception {
        entityProperties.getPersist().setContracts(false);
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertContractTransaction(transactionBody, record, false)
        );
    }

    // Test for bad entity id in a failed transaction
    @Test
    void cryptoTransferBadContractId() throws Exception {
        Transaction transaction = contractCallTransaction(ContractID.newBuilder().setContractNum(-1L).build());
        var transactionBody = TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.INVALID_CONTRACT_ID);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(3, entityRepository.count()), // payer, node, treasury
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId())
        );
    }

    private void assertContractTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Entities actualContract = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertContract(record.getReceipt().getContractID(), actualContract),
                () -> assertEquals(deleted, actualContract.isDeleted()));
    }

    private void assertFailedContractCreate(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(transactionBody.getContractCreateInstance().getInitialBalance(),
                        dbTransaction.getInitialBalance()));
    }

    private void assertContractEntity(ContractCreateTransactionBody expected, Timestamp consensusTimestamp) {
        var dbTransaction = getDbTransaction(consensusTimestamp);
        Entities actualContract = getEntity(dbTransaction.getEntityId());
        Entities actualProxyAccount = getEntity(actualContract.getProxyAccountId());
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualContract.getAutoRenewPeriod()),
                () -> assertArrayEquals(expected.getAdminKey().toByteArray(), actualContract.getKey()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccount),
                () -> assertEquals(expected.getMemo(), actualContract.getMemo()),
                () -> assertEquals(expected.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertNull(actualContract.getExpiryTimeNs()));
    }

    private void assertContractEntity(ContractUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entities actualContract = getTransactionEntity(consensusTimestamp);
        Entities actualProxyAccount = getEntity(actualContract.getProxyAccountId());
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualContract.getAutoRenewPeriod()),
                () -> assertArrayEquals(expected.getAdminKey().toByteArray(), actualContract.getKey()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccount),
                () -> assertEquals(expected.getMemo(), actualContract.getMemo()),
                () -> assertEquals(
                        Utility.timeStampInNanos(expected.getExpirationTime()), actualContract.getExpiryTimeNs()));
    }

    private void assertContractEntityHasNullFields(Timestamp consensusTimestamp) {
        Entities actualContract = getTransactionEntity(consensusTimestamp);
        assertAll(
                () -> assertNull(actualContract.getKey()),
                () -> assertNull(actualContract.getExpiryTimeNs()),
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
            recordBuilder.getReceiptBuilder().setContractID(contractId);
            buildContractFunctionResult(recordBuilder.getContractCreateResultBuilder());
        }, transactionBody, status.getNumber());
    }

    private TransactionRecord callRecord(TransactionBody transactionBody) {
        return callRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord callRecord(TransactionBody transactionBody, ResponseCodeEnum status) {
        return buildTransactionRecord(recordBuilder -> {
            recordBuilder.getReceiptBuilder().setContractID(contractId);
            buildContractFunctionResult(recordBuilder.getContractCallResultBuilder());
        }, transactionBody, status.getNumber());
    }

    private void buildContractFunctionResult(ContractFunctionResult.Builder builder) {
        builder.setBloom(ByteString.copyFromUtf8("bloom"));
        builder.setContractCallResult(ByteString.copyFromUtf8("call result"));
        builder.setContractID(contractId);
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
            contractCreate.setFileID(fileId);
            contractCreate.setGas(10000L);
            contractCreate.setInitialBalance(20000L);
            contractCreate.setMemo("Contract Memo");
            contractCreate.setNewRealmAdminKey(keyFromString(KEY2));
            contractCreate.setProxyAccountID(
                    AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1003).build());
            contractCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
            contractCreate.setShardID(ShardID.newBuilder().setShardNum(0));
        });
    }

    private Transaction contractUpdateAllTransaction() {
        return buildTransaction(builder -> {
            ContractUpdateTransactionBody.Builder contractUpdate = builder.getContractUpdateInstanceBuilder();
            contractUpdate.setAdminKey(keyFromString(KEY));
            contractUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(400).build());
            contractUpdate.setContractID(contractId);
            contractUpdate.setExpirationTime(Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
            contractUpdate.setFileID(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());
            contractUpdate.setMemo("contract update memo");
            contractUpdate.setProxyAccountID(
                    AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3000).build());
        });
    }

    private Transaction contractDeleteTransaction() {
        return buildTransaction(builder -> {
            ContractDeleteTransactionBody.Builder contractDelete = builder.getContractDeleteInstanceBuilder();
            contractDelete.setContractID(contractId);
        });
    }

    private Transaction contractCallTransaction() {
        return contractCallTransaction(contractId);
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
}
