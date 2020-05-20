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
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.KeyList;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public class EntityRecordItemListenerCryptoTest extends AbstractEntityRecordItemListenerTest {

    //TODO: These transaction data items are not saved to the database
    //  cryptoCreateTransactionBody.getReceiveRecordThreshold()
    //  cryptoCreateTransactionBody.getShardID()
    //  cryptoCreateTransactionBody.getRealmID()
    //  cryptoCreateTransactionBody.getNewRealmAdminKey()
    //  cryptoCreateTransactionBody.getReceiverSigRequired()
    //  cryptoCreateTransactionBody.getSendRecordThreshold()
    //  cryptoCreateTransactionBody.getReceiveRecordThreshold()
    //  transactionBody.getTransactionFee()
    //  transactionBody.getTransactionValidDuration()
    //  transaction.getSigMap()
    //  record.getTransactionHash();

    private static final AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1001)
            .build();
    private static final String memo = "Crypto test memo";
    private static final long[] transferAccounts = {98, 2002, 3};
    private static final long[] transferAmounts = {1000, -2000, 20};

    @BeforeEach
    void before() {
        entityProperties.getPersist().setClaims(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void cryptoCreate() throws Exception {

        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbNewAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbNewAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbNewAccountEntity)

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbNewAccountEntity
                        .getAutoRenewPeriod())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(cryptoCreateTransactionBody.getKey()
                        .toByteArray()), dbNewAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoCreateTransactionBody.getKey().toByteArray(), dbNewAccountEntity
                        .getKey())

                // Additional entity checks
                , () -> assertFalse(dbNewAccountEntity.isDeleted())
                , () -> assertNull(dbNewAccountEntity.getExpiryTimeNs())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoCreateTransactionWithBody() throws Exception {

        Transaction transaction = createTransactionWithBody();
        TransactionBody transactionBody = transaction.getBody();
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbNewAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbNewAccountEntity.getProxyAccountId()).get();
        CryptoTransfer dbCryptoTransfer = cryptoTransferRepository
                .findByConsensusTimestampAndEntityNum(
                        Utility.timeStampInNanos(record.getConsensusTimestamp()),
                        dbNewAccountEntity.getEntityNum()).get();
        AccountID recordReceiptAccountId = record.getReceipt().getAccountID();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbNewAccountEntity)

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbNewAccountEntity
                        .getAutoRenewPeriod())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(cryptoCreateTransactionBody.getKey()
                        .toByteArray()), dbNewAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoCreateTransactionBody.getKey().toByteArray(), dbNewAccountEntity
                        .getKey())

                // Additional entity checks
                , () -> assertFalse(dbNewAccountEntity.isDeleted())
                , () -> assertNull(dbNewAccountEntity.getExpiryTimeNs())

                // Crypto transfer list
                , () -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbCryptoTransfer
                        .getConsensusTimestamp())
                , () -> assertEquals(recordReceiptAccountId.getRealmNum(), dbCryptoTransfer.getRealmNum())
                , () -> assertEquals(recordReceiptAccountId.getAccountNum(), dbCryptoTransfer.getEntityNum())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoCreateFailedTransaction() throws Exception {

        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        // Clear receipt.accountID since transaction is failure.
        TransactionRecord.Builder recordBuilder = transactionRecord(
                transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE).toBuilder();
        recordBuilder.getReceiptBuilder().clearAccountID();
        TransactionRecord record = recordBuilder.build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository.findById(
                Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(3, entityRepository.count())
                , () -> assertEquals(2, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                , () -> assertNull(dbTransaction.getEntityId())
                // record inputs
                , () -> assertRecord(record)

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoCreateInitialBalanceInTransferList() throws Exception {

        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord tempRecord = transactionRecordSuccess(transactionBody);

        // add initial balance to transfer list
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();

        TransferList.Builder transferList = tempRecord.getTransferList().toBuilder();
        transferList.addAccountAmounts(AccountAmount.newBuilder().setAccountID(accountId).setAmount(initialBalance));
        transferList.addAccountAmounts(AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(202))
                .setAmount(-initialBalance));
        TransactionRecord record = tempRecord.toBuilder().setTransferList(transferList).build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbNewAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbNewAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(7, entityRepository.count())
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbNewAccountEntity)

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbNewAccountEntity
                        .getAutoRenewPeriod())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(cryptoCreateTransactionBody.getKey()
                        .toByteArray()), dbNewAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoCreateTransactionBody.getKey().toByteArray(), dbNewAccountEntity
                        .getKey())

                // Additional entity checks
                , () -> assertFalse(dbNewAccountEntity.isDeleted())
                , () -> assertNull(dbNewAccountEntity.getExpiryTimeNs())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoCreateTwice() throws Exception {

        Transaction firstTransaction = cryptoCreateTransaction();
        TransactionBody firstTransactionBody = TransactionBody.parseFrom(firstTransaction.getBodyBytes());
        TransactionRecord firstRecord = transactionRecordSuccess(firstTransactionBody);

        parseRecordItemAndCommit(new RecordItem(firstTransaction, firstRecord));

        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbNewAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbNewAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(10, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbNewAccountEntity)

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getAutoRenewPeriod().getSeconds(), dbNewAccountEntity
                        .getAutoRenewPeriod())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(cryptoCreateTransactionBody.getKey()
                        .toByteArray()), dbNewAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoCreateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoCreateTransactionBody.getKey().toByteArray(), dbNewAccountEntity
                        .getKey())

                // Additional entity checks
                , () -> assertFalse(dbNewAccountEntity.isDeleted())
                , () -> assertNull(dbNewAccountEntity.getExpiryTimeNs())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoUpdateSuccessfulTransaction() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now update
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoUpdateTransactionBody cryptoUpdateTransactionBody = transactionBody.getCryptoUpdateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(7, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertEquals(cryptoUpdateTransactionBody.getAutoRenewPeriod().getSeconds(), dbAccountEntity
                        .getAutoRenewPeriod())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(cryptoUpdateTransactionBody.getKey()
                        .toByteArray()), dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoUpdateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoUpdateTransactionBody.getKey().toByteArray(), dbAccountEntity.getKey())
                , () -> assertEquals(Utility
                        .timeStampInNanos(cryptoUpdateTransactionBody.getExpirationTime()), dbAccountEntity
                        .getExpiryTimeNs())

                // Additional entity checks
                , () -> assertFalse(dbAccountEntity.isDeleted())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    /**
     * Github issue #483
     */
    @Test
    void samePayerAndUpdateAccount() throws Exception {
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        transactionBody = TransactionBody.newBuilder()
                .mergeFrom(transactionBody)
                .setTransactionID(Utility.getTransactionId(accountId))
                .build();
        transaction = Transaction.newBuilder().setBodyBytes(transactionBody.toByteString()).build();
        CryptoUpdateTransactionBody cryptoUpdateTransactionBody = transactionBody.getCryptoUpdateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    // Transactions in production have proxyAccountID explicitly set to '0.0.0'. Test is to prevent code regression
    // in handling this weird case.
    @Test
    void proxyAccountIdSetTo0() throws Exception {
        // given
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        var bodyBuilder = transactionBody.toBuilder();
        bodyBuilder.getCryptoUpdateAccountBuilder().setProxyAccountID(AccountID.getDefaultInstance());
        transactionBody = bodyBuilder.build();
        transaction = Transaction.newBuilder().setBodyBytes(transactionBody.toByteString()).build();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        // then: process the transaction without throwing NPE
        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    @DisplayName("update account such that expiration timestamp overflows nanos_timestamp")
    @ParameterizedTest(name = "with seconds {0} and expectedNanosTimestamp {1}")
    @CsvSource({
            "9223372036854775807, 9223372036854775807",
            "31556889864403199, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808",
            "-1000000000000000000, -9223372036854775808"
    })
    void cryptoUpdateExpirationOverflow(long seconds, long expectedNanosTimestamp) throws Exception {

        // first create the account
        var createTransaction = cryptoCreateTransaction();
        var createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        var createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now update
        var updateTransaction = Transaction.newBuilder();
        var updateTransactionBody = CryptoUpdateTransactionBody.newBuilder();
        updateTransactionBody.setAccountIDToUpdate(accountId);

        // *** THIS IS THE OVERFLOW WE WANT TO TEST ***
        // This should result in the entity having a Long.MAX_VALUE or Long.MIN_VALUE expirations (the results of
        // overflows).
        updateTransactionBody.setExpirationTime(Timestamp.newBuilder().setSeconds(seconds));

        var transactionBody = defaultTransactionBodyBuilder(memo);
        transactionBody.setCryptoUpdateAccount(updateTransactionBody.build());
        updateTransaction.setBodyBytes(transactionBody.build().toByteString());
        var rec = transactionRecordSuccess(transactionBody.build());

        parseRecordItemAndCommit(new RecordItem(updateTransaction.build(), rec));

        var dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(rec.getConsensusTimestamp())).get();
        var dbAccountEntity = entityRepository.findById(dbTransaction.getEntityId()).get();

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpiryTimeNs())
        );
    }

    @Test
    void cryptoUpdateFailedTransaction() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now update
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbCreateTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(createRecord.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntityBefore = entityRepository
                .findById(dbCreateTransaction.getEntityId()).get();

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(7, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)
                // no changes to entity
                , () -> assertEquals(dbAccountEntityBefore, dbAccountEntity)
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoDeleteSuccessfulTransaction() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertTrue(dbAccountEntity.isDeleted())

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpiryTimeNs())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoDeleteFailedTransaction() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        com.hedera.mirror.importer.domain.Entities dbProxyAccountId = entityRepository
                .findById(dbAccountEntity.getProxyAccountId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(7, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertFalse(dbAccountEntity.isDeleted())

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpiryTimeNs())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoAddLiveHashPersist() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now add live hash
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();
        LiveHash dbLiveHash = liveHashRepository.findById(dbTransaction.getConsensusNs()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(1, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertAccount(cryptoAddLiveHashTransactionBody.getLiveHash().getAccountId(), dbAccountEntity)
                // TODO (issue #303) cryptoAddClaimTransactionBody.getClaim().getClaimDuration()
                , () -> assertArrayEquals(cryptoAddLiveHashTransactionBody.getLiveHash().getHash()
                        .toByteArray(), dbLiveHash
                        .getLivehash())
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoAddLiveHashDoNotPersist() throws Exception {
        entityProperties.getPersist().setClaims(false);
        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now add live hash
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        com.hedera.mirror.importer.domain.Entities dbAccountEntity = entityRepository
                .findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertAccount(cryptoAddLiveHashTransactionBody.getLiveHash().getAccountId(), dbAccountEntity)
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoDeleteLiveHash() throws Exception {

        // first create the account
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // add a live hash
        Transaction transactionAddLiveHash = cryptoAddLiveHashTransaction();
        TransactionBody transactionBodyAddLiveHash = TransactionBody.parseFrom(transactionAddLiveHash.getBodyBytes());
        TransactionRecord recordAddLiveHash = transactionRecordSuccess(transactionBodyAddLiveHash);

        parseRecordItemAndCommit(new RecordItem(transactionAddLiveHash, recordAddLiveHash));

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoDeleteLiveHashTransactionBody deleteLiveHashTransactionBody = transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        Entities dbAccountEntity = entityRepository.findById(dbTransaction.getEntityId()).get();

        assertAll(
                // row counts
                () -> assertEquals(3, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(11, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(1, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)

                // transaction body inputs
                , () -> assertAccount(deleteLiveHashTransactionBody.getAccountOfLiveHash(), dbAccountEntity)
                // TODO (issue #303) check deleted
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoTransferWithPersistence() throws Exception {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(4, entityRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
        );

        // Crypto transfer list
        verifyRepoCryptoTransferList(record);
    }

    @Test
    void cryptoTransferWithoutPersistence() throws Exception {
        entityProperties.getPersist().setCryptoTransferAmounts(false);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                // row counts
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(2, entityRepository.count())
                , () -> assertEquals(0, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())

                // transaction
                , () -> assertTransaction(transactionBody, dbTransaction)
                // record inputs
                , () -> assertRecord(record)
        );

        // Crypto transfer list
        CryptoTransfer tempDbCryptoTransfer;
        TransferList transferList = record.getTransferList();
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();

            boolean isPresent = cryptoTransferRepository.findByConsensusTimestampAndEntityNum(
                    Utility.timeStampInNanos(record.getConsensusTimestamp()),
                    accountId.getAccountNum()).isPresent();

            assertEquals(false, isPresent);
        }
    }

    @Test
    void cryptoTransferFailedTransaction() throws Exception {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INVALID_ACCOUNT_ID);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(3, entityRepository.count(), "Payer, node and treasury"),
                () -> assertEquals(2, cryptoTransferRepository.count(), "Node and network fee"),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransaction(transactionBody, dbTransaction),
                () -> assertRecord(record),
                () -> verifyRepoCryptoTransferList(record)
        );
    }

    @Test
    void unknownTransactionResult() throws Exception {
        int unknownResult = -1000;
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecord(transactionBody, unknownResult);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        org.assertj.core.api.Assertions.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .extracting(com.hedera.mirror.importer.domain.Transaction::getResult)
                .containsOnly(unknownResult);
    }

    private TransactionRecord transactionRecordSuccess(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum responseCode) {
        return transactionRecord(transactionBody, responseCode.getNumber());
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int responseCode) {
        TransactionRecord.Builder record = TransactionRecord.newBuilder();

        // record
        Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
        TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

        // Build the record
        record.setConsensusTimestamp(consensusTimeStamp);
        record.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        receipt.setAccountID(accountId);
        receipt.setStatusValue(responseCode);

        record.setReceipt(receipt.build());
        record.setTransactionFee(transactionBody.getTransactionFee());
        record.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        record.setTransactionID(transactionBody.getTransactionID());

        TransferList.Builder transferList = TransferList.newBuilder();

        for (int i = 0; i < transferAccounts.length; i++) {
            // Unsuccessful transactions will only include node and network fees to system accounts
            if (responseCode == ResponseCodeEnum.SUCCESS_VALUE || transferAccounts[i] < 1000) {
                AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
                accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0)
                        .setAccountNum(transferAccounts[i]));
                accountAmount.setAmount(transferAmounts[i]);
                transferList.addAccountAmounts(accountAmount);
            }
        }

        record.setTransferList(transferList);

        return record.build();
    }

    private Transaction cryptoCreateTransaction() {

        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoCreateTransactionBody.Builder cryptoCreate = CryptoCreateTransactionBody.newBuilder();

        // Build a transaction
        cryptoCreate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L));
        cryptoCreate.setInitialBalance(1000L);
        cryptoCreate.setKey(keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"));
        cryptoCreate
                .setNewRealmAdminKey(keyFromString(
                        "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"));
        cryptoCreate.setProxyAccountID(AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3));
        cryptoCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
        cryptoCreate.setShardID(ShardID.newBuilder().setShardNum(0));
        cryptoCreate.setReceiveRecordThreshold(2000L);
        cryptoCreate.setReceiverSigRequired(true);
        cryptoCreate.setSendRecordThreshold(3000L);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);

        // body transaction
        body.setCryptoCreateAccount(cryptoCreate.build());
        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction createTransactionWithBody() {

        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoCreateTransactionBody.Builder cryptoCreate = CryptoCreateTransactionBody.newBuilder();

        // Build a transaction
        cryptoCreate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L));
        cryptoCreate.setInitialBalance(1000L);
        cryptoCreate.setKey(keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"));
        cryptoCreate
                .setNewRealmAdminKey(keyFromString(
                        "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"));
        cryptoCreate.setProxyAccountID(AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3));
        cryptoCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
        cryptoCreate.setShardID(ShardID.newBuilder().setShardNum(0));
        cryptoCreate.setReceiveRecordThreshold(2000L);
        cryptoCreate.setReceiverSigRequired(true);
        cryptoCreate.setSendRecordThreshold(3000L);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);

        // body transaction
        body.setCryptoCreateAccount(cryptoCreate.build());
        transaction.setBody(body.build());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction cryptoUpdateTransaction() {

        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoUpdateTransactionBody.Builder cryptoUpdate = CryptoUpdateTransactionBody.newBuilder();
        String key = "0a2312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110aaa";

        // Build a transaction
        cryptoUpdate.setAccountIDToUpdate(accountId);
        cryptoUpdate.setAutoRenewPeriod(Duration.newBuilder().setSeconds(5001L));
        cryptoUpdate.setExpirationTime(Utility.instantToTimestamp(Instant.now()));
        cryptoUpdate.setKey(keyFromString(key));
        cryptoUpdate.setProxyAccountID(AccountID.newBuilder().setShardNum(5).setRealmNum(6).setAccountNum(8));
        cryptoUpdate.setReceiveRecordThreshold(5001L);
        cryptoUpdate.setReceiverSigRequired(false);
        cryptoUpdate.setSendRecordThreshold(6001L);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setCryptoUpdateAccount(cryptoUpdate.build());

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction cryptoDeleteTransaction() {

        // transaction id
        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoDeleteTransactionBody.Builder cryptoDelete = CryptoDeleteTransactionBody.newBuilder();

        // Build a transaction
        cryptoDelete.setDeleteAccountID(accountId);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setCryptoDelete(cryptoDelete.build());

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction cryptoAddLiveHashTransaction() {

        // transaction id
        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoAddLiveHashTransactionBody.Builder cryptoLiveHash = CryptoAddLiveHashTransactionBody.newBuilder();

        // Build a live hash
        com.hederahashgraph.api.proto.java.LiveHash.Builder liveHash = com.hederahashgraph.api.proto.java.LiveHash
                .newBuilder();
        liveHash.setAccountId(accountId);
        liveHash.setDuration(Duration.newBuilder().setSeconds(10000L));
        liveHash.setHash(ByteString.copyFromUtf8("live hash"));
        KeyList.Builder keyList = KeyList.newBuilder();
        keyList.addKeys(keyFromString("0a2312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110aaa"));
        liveHash.setKeys(keyList);

        // Build a transaction
        cryptoLiveHash.setLiveHash(liveHash);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setCryptoAddLiveHash(cryptoLiveHash);

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction cryptoDeleteLiveHashTransaction() {

        // transaction id
        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoDeleteLiveHashTransactionBody.Builder cryptoDeleteLiveHash = CryptoDeleteLiveHashTransactionBody
                .newBuilder();

        // Build a transaction
        cryptoDeleteLiveHash.setAccountOfLiveHash(accountId);
        cryptoDeleteLiveHash.setLiveHashToDelete(ByteString.copyFromUtf8("live hash"));

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setCryptoDeleteLiveHash(cryptoDeleteLiveHash);

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private Transaction cryptoTransferTransaction() {

        // transaction id
        Transaction.Builder transaction = Transaction.newBuilder();
        CryptoTransferTransactionBody.Builder cryptoTransferBody = CryptoTransferTransactionBody.newBuilder();

        // Build a transaction
        TransferList.Builder transferList = TransferList.newBuilder();

        for (int i = 0; i < transferAccounts.length; i++) {
            AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
            accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0)
                    .setAccountNum(transferAccounts[i]));
            accountAmount.setAmount(transferAmounts[i]);
            transferList.addAccountAmounts(accountAmount);
        }

        cryptoTransferBody.setTransfers(transferList);

        // Transaction body
        TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);
        // body transaction
        body.setCryptoTransfer(cryptoTransferBody);

        transaction.setBodyBytes(body.build().toByteString());
        transaction.setSigMap(getSigMap());

        return transaction.build();
    }

    private void verifyRepoCryptoTransferList(TransactionRecord record) {
        CryptoTransfer tempDbCryptoTransfer;
        TransferList transferList = record.getTransferList();
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();

            tempDbCryptoTransfer = cryptoTransferRepository.findByConsensusTimestampAndEntityNum(
                    Utility.timeStampInNanos(record.getConsensusTimestamp()),
                    accountId.getAccountNum()).get();

            assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), tempDbCryptoTransfer
                    .getConsensusTimestamp());
            assertEquals(aa.getAmount(), tempDbCryptoTransfer.getAmount());
            assertEquals(accountId.getRealmNum(), tempDbCryptoTransfer.getRealmNum());
            assertEquals(accountId.getAccountNum(), tempDbCryptoTransfer.getEntityNum());
        }
    }

    @Test
    void cryptoTransferPersistRawBytesDefault() throws Exception {
        // Use the default properties for record parsing - the raw bytes should NOT be stored in the db
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    @Test
    void cryptoTransferPersistRawBytesTrue() throws Exception {
        // Explicitly persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(true);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, transaction.toByteArray());
    }

    @Test
    void cryptoTransferPersistRawBytesFalse() throws Exception {
        // Explicitly DO NOT persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(false);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    private void testRawBytes(Transaction transaction, byte[] expectedBytes) throws Exception {
        // given
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        // when
        parseRecordItemAndCommit(new RecordItem(transaction.toByteArray(), record.toByteArray()));

        // then
        com.hedera.mirror.importer.domain.Transaction dbTransaction = transactionRepository
                .findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();
        assertArrayEquals(expectedBytes, dbTransaction.getTransactionBytes());
    }
}
