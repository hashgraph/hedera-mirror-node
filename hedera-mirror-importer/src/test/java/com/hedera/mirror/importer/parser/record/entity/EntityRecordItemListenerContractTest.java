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
import com.hederahashgraph.api.proto.java.AccountAmount;
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
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final ContractID contractId = ContractID.newBuilder().setShardNum(0).setRealmNum(0)
            .setContractNum(1001).build();
    private static final FileID fileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1002).build();
    private static final String realmAdminKey =
            "112212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final String memo = "Contract test memo";

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
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbContractEntity.getProxyAccountId()).get();
        ContractResult dbContractResults = contractResultRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)
                , () -> assertArrayEquals(record.getContractCreateResult().toByteArray(), dbContractResults
                        .getCallResult())
                , () -> assertEquals(record.getContractCreateResult().getGasUsed(), dbContractResults.getGasUsed())

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertEquals(contractCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbContractEntity
                        .getAutoRenewPeriod())
                , () -> assertArrayEquals(contractCreateTransactionBody.getAdminKey().toByteArray(), dbContractEntity
                        .getKey())
                , () -> assertEquals(contractCreateTransactionBody.getMemo(), dbContractEntity.getMemo())
                , () -> assertAccount(contractCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(contractCreateTransactionBody.getConstructorParameters()
                        .toByteArray(), dbContractResults.getFunctionParameters())
                , () -> assertEquals(contractCreateTransactionBody.getGas(), dbContractResults.getGasSupplied())
                , () -> assertEquals(contractCreateTransactionBody.getInitialBalance(), dbTransaction
                        .getInitialBalance())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractCreateInvalidTransaction() throws Exception {

        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        // Clear receipt.contractID since transaction is failure.
        TransactionRecord.Builder recordBuilder = createOrUpdateRecord(
                transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE).toBuilder();
        recordBuilder.getReceiptBuilder().clearContractID();
        TransactionRecord record = recordBuilder.build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                , () -> assertNull(dbTransaction.getEntityId())

                // record inputs
                , () -> assertRecord(record)

                // transaction body inputs
                , () -> assertEquals(contractCreateTransactionBody.getInitialBalance(),
                        dbTransaction.getInitialBalance())
        );
    }

    @Test
    @Disabled
    void contractCreateDoNotPersist() throws Exception {
        entityProperties.getPersist().setContracts(false);

        Transaction transaction = contractCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        ContractCreateTransactionBody contractCreateTransactionBody = transactionBody.getContractCreateInstance();
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbContractEntity.getProxyAccountId()).get();
        Optional<ContractResult> dbContractResults = contractResultRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                , () -> assertFalse(dbContractResults.isPresent())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertEquals(contractCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbContractEntity
                        .getAutoRenewPeriod())
                , () -> assertArrayEquals(contractCreateTransactionBody.getAdminKey().toByteArray(), dbContractEntity
                        .getKey())
                , () -> assertAccount(contractCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertEquals(contractCreateTransactionBody.getInitialBalance(), dbTransaction
                        .getInitialBalance())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractUpdateAllToExisting() throws Exception {

        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody
                .parseFrom(contractCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbContractEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(7, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertEquals(contractUpdateTransactionBody.getAutoRenewPeriod().getSeconds(), dbContractEntity
                        .getAutoRenewPeriod())
                , () -> assertArrayEquals(contractUpdateTransactionBody.getAdminKey().toByteArray(), dbContractEntity
                        .getKey())
                , () -> assertEquals(contractUpdateTransactionBody.getMemo(), dbContractEntity.getMemo())
                , () -> assertAccount(contractUpdateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertEquals(Utility
                        .timeStampInNanos(contractUpdateTransactionBody.getExpirationTime()), dbContractEntity
                        .getExpiryTimeNs())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractUpdateAllToNew() throws Exception {

        // now update
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbContractEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertEquals(contractUpdateTransactionBody.getAutoRenewPeriod().getSeconds(), dbContractEntity
                        .getAutoRenewPeriod())
                , () -> assertArrayEquals(contractUpdateTransactionBody.getAdminKey().toByteArray(), dbContractEntity
                        .getKey())
                , () -> assertAccount(contractUpdateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertEquals(Utility
                        .timeStampInNanos(contractUpdateTransactionBody.getExpirationTime()), dbContractEntity
                        .getExpiryTimeNs())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractUpdateAllToExistingInvalidTransaction() throws Exception {

        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody
                .parseFrom(contractCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);
        ContractCreateTransactionBody contractCreateTransactionBody = createTransactionBody
                .getContractCreateInstance();

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractUpdateAllTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbContractEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertEquals(contractCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbContractEntity
                        .getAutoRenewPeriod())
                , () -> assertArrayEquals(contractCreateTransactionBody.getAdminKey().toByteArray(), dbContractEntity
                        .getKey())
                , () -> assertAccount(contractCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
                , () -> assertNull(dbContractEntity.getExpiryTimeNs())
        );
    }

    @Test
    void contractDeleteToExisting() throws Exception {

        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody
                .parseFrom(contractCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now update
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertTrue(dbContractEntity.isDeleted())

                // Additional entity checks
                , () -> assertNotNull(dbContractEntity.getKey())
                , () -> assertNull(dbContractEntity.getExpiryTimeNs())
                , () -> assertNotNull(dbContractEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbContractEntity.getProxyAccountId())
        );
    }

    @Test
    void contractDeleteToNew() throws Exception {

        // now update
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertTrue(dbContractEntity.isDeleted())

                // Additional entity checks
                , () -> assertNull(dbContractEntity.getKey())
                , () -> assertNull(dbContractEntity.getExpiryTimeNs())
                , () -> assertNull(dbContractEntity.getAutoRenewPeriod())
                , () -> assertNull(dbContractEntity.getProxyAccountId())
        );
    }

    @Test
    void contractDeleteToNewInvalidTransaction() throws Exception {

        // now update
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = createOrUpdateRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertFalse(dbContractEntity.isDeleted())

                // Additional entity checks
                , () -> assertNull(dbContractEntity.getKey())
                , () -> assertNull(dbContractEntity.getExpiryTimeNs())
                , () -> assertNull(dbContractEntity.getAutoRenewPeriod())
                , () -> assertNull(dbContractEntity.getProxyAccountId())
        );
    }

    @Test
    void contractCallToExisting() throws Exception {

        // first create the contract
        Transaction contractCreateTransaction = contractCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody
                .parseFrom(contractCreateTransaction.getBodyBytes());
        TransactionRecord recordCreate = createOrUpdateRecord(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(contractCreateTransaction, recordCreate));

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        ContractResult dbContractResults = contractResultRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(2, contractResultRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)
                , () -> assertArrayEquals(record.getContractCallResult().toByteArray(), dbContractResults
                        .getCallResult())
                , () -> assertEquals(record.getContractCallResult().getGasUsed(), dbContractResults.getGasUsed())

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertArrayEquals(contractCallTransactionBody.getFunctionParameters()
                        .toByteArray(), dbContractResults.getFunctionParameters())
                , () -> assertEquals(contractCallTransactionBody.getGas(), dbContractResults.getGasSupplied())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractCallToNew() throws Exception {

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);
        ContractCallTransactionBody contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        ContractResult dbContractResults = contractResultRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)
                , () -> assertArrayEquals(record.getContractCallResult().toByteArray(), dbContractResults
                        .getCallResult())
                , () -> assertEquals(record.getContractCallResult().getGasUsed(), dbContractResults.getGasUsed())

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // transaction body inputs
                , () -> assertArrayEquals(contractCallTransactionBody.getFunctionParameters()
                        .toByteArray(), dbContractResults.getFunctionParameters())
                , () -> assertEquals(contractCallTransactionBody.getGas(), dbContractResults.getGasSupplied())

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    void contractCallToNewInvalidTransaction() throws Exception {

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = callRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbContractEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(1, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    @Test
    @Disabled
    void contractCallDoNotPersist() throws Exception {
        entityProperties.getPersist().setContracts(false);

        // now call
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = callRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        Entities dbContractEntity = entityRepository.findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                , () -> assertRecord(record)

                // receipt
                , () -> assertContract(record.getReceipt().getContractID(), dbContractEntity)

                // Additional entity checks
                , () -> assertFalse(dbContractEntity.isDeleted())
        );
    }

    private TransactionRecord createOrUpdateRecord(TransactionBody transactionBody) {
        return createOrUpdateRecord(transactionBody, contractId, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord createOrUpdateRecord(TransactionBody transactionBody, ResponseCodeEnum result) {
        return createOrUpdateRecord(transactionBody, contractId, result);
    }

    private TransactionRecord createOrUpdateRecord(TransactionBody transactionBody, ContractID newContractId,
                                                   ResponseCodeEnum result) {
        TransactionRecord.Builder record = TransactionRecord.newBuilder();

        // record
        Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
        long[] transferAccounts = {98, 2002, 3};
        long[] transferAmounts = {1000, -2000, 20};
        ResponseCodeEnum responseCode = result;
        TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

        // Build the record
        record.setConsensusTimestamp(consensusTimeStamp);
        record.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        receipt.setContractID(newContractId);
        receipt.setStatus(responseCode);

        record.setReceipt(receipt.build());
        record.setTransactionFee(transactionBody.getTransactionFee());
        record.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        record.setTransactionID(transactionBody.getTransactionID());

        ContractFunctionResult.Builder contractFunctionResult = ContractFunctionResult.newBuilder();
        contractFunctionResult.setBloom(ByteString.copyFromUtf8("bloom"));
        contractFunctionResult.setContractCallResult(ByteString.copyFromUtf8("create result"));
        contractFunctionResult.setContractID(contractId);
        contractFunctionResult.setErrorMessage("create error message");
        contractFunctionResult.setGasUsed(30);

        ContractLoginfo.Builder contractLogInfo = ContractLoginfo.newBuilder();
        contractLogInfo.addTopic(ByteString.copyFromUtf8("Topic"));

        contractFunctionResult.addLogInfo(contractLogInfo.build());
        record.setContractCreateResult(contractFunctionResult.build());

        TransferList.Builder transferList = TransferList.newBuilder();

        for (int i = 0; i < transferAccounts.length; i++) {
            AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
            accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0)
                    .setAccountNum(transferAccounts[i]));
            accountAmount.setAmount(transferAmounts[i]);
            transferList.addAccountAmounts(accountAmount);
        }

        record.setTransferList(transferList);

        return record.build();
    }

    private TransactionRecord callRecord(TransactionBody transactionBody) {
        return callRecord(transactionBody, contractId, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord callRecord(TransactionBody transactionBody, ResponseCodeEnum result) {
        return callRecord(transactionBody, contractId, result);
    }

    private TransactionRecord callRecord(TransactionBody transactionBody, ContractID newContractId,
                                         ResponseCodeEnum result) {
        TransactionRecord.Builder record = TransactionRecord.newBuilder();

        // record
        Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
        long[] transferAccounts = {98, 2002, 3};
        long[] transferAmounts = {1000, -2000, 20};
        ResponseCodeEnum responseCode = result;
        TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

        // Build the record
        record.setConsensusTimestamp(consensusTimeStamp);
        record.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        receipt.setContractID(newContractId);
        receipt.setStatus(responseCode);

        record.setReceipt(receipt.build());
        record.setTransactionFee(transactionBody.getTransactionFee());
        record.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        record.setTransactionID(transactionBody.getTransactionID());

        ContractFunctionResult.Builder contractFunctionResult = ContractFunctionResult.newBuilder();
        contractFunctionResult.setBloom(ByteString.copyFromUtf8("bloom"));
        contractFunctionResult.setContractCallResult(ByteString.copyFromUtf8("call result"));
        contractFunctionResult.setContractID(contractId);
        contractFunctionResult.setErrorMessage("call error message");
        contractFunctionResult.setGasUsed(30);

        ContractLoginfo.Builder contractLogInfo = ContractLoginfo.newBuilder();
        contractLogInfo.addTopic(ByteString.copyFromUtf8("Topic"));

        contractFunctionResult.addLogInfo(contractLogInfo.build());
        record.setContractCallResult(contractFunctionResult.build());

        TransferList.Builder transferList = TransferList.newBuilder();

        for (int i = 0; i < transferAccounts.length; i++) {
            AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
            accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0)
                    .setAccountNum(transferAccounts[i]));
            accountAmount.setAmount(transferAmounts[i]);
            transferList.addAccountAmounts(accountAmount);
        }

        record.setTransferList(transferList);

        return record.build();
    }

    private Transaction contractCreateTransaction() {

        Transaction.Builder transaction = Transaction.newBuilder();
        ContractCreateTransactionBody.Builder contractCreate = ContractCreateTransactionBody.newBuilder();

        String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

        // Build a transaction
        contractCreate.setAdminKey(keyFromString(key));
        contractCreate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(100).build());
        contractCreate.setConstructorParameters(ByteString.copyFromUtf8("Constructor Parameters"));
        contractCreate.setFileID(fileId);
        contractCreate.setGas(10000L);
        contractCreate.setInitialBalance(20000L);
        contractCreate.setMemo("Contract Memo");
        contractCreate.setNewRealmAdminKey(keyFromString(realmAdminKey));
        contractCreate
                .setProxyAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1003).build());
        contractCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
        contractCreate.setShardID(ShardID.newBuilder().setShardNum(0));

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);

        // body transaction
        body.setContractCreateInstance(contractCreate.build());
        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction contractUpdateAllTransaction() {

        Transaction.Builder transaction = Transaction.newBuilder();
        ContractUpdateTransactionBody.Builder contractUpdate = ContractUpdateTransactionBody.newBuilder();
        String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";

        // Build a transaction
        contractUpdate.setAdminKey(keyFromString(key));
        contractUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(400).build());
        contractUpdate.setContractID(contractId);
        contractUpdate.setExpirationTime(Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
        contractUpdate.setFileID(FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(2000).build());
        contractUpdate.setMemo("contract update memo");
        contractUpdate
                .setProxyAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3000).build());

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setContractUpdateInstance(contractUpdate);

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction contractDeleteTransaction() {

        // transaction id
        Transaction.Builder transaction = Transaction.newBuilder();
        ContractDeleteTransactionBody.Builder contractDelete = ContractDeleteTransactionBody.newBuilder();

        // Build a transaction
        contractDelete.setContractID(contractId);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setContractDeleteInstance(contractDelete.build());

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction contractCallTransaction() {

        Transaction.Builder transaction = Transaction.newBuilder();
        ContractCallTransactionBody.Builder contractCall = ContractCallTransactionBody.newBuilder();

        // Build a transaction
        contractCall.setAmount(88889);
        contractCall.setContractID(contractId);
        contractCall.setFunctionParameters(ByteString.copyFromUtf8("Call Parameters"));
        contractCall.setGas(33333);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);

        // body transaction
        body.setContractCall(contractCall.build());
        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }
}
