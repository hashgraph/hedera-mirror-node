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
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
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
    private static final AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1001)
            .build();
    private static final long[] additionalTransfers = {5000, 6000, PAYER.getAccountNum()};
    private static final long[] additionalTransferAmounts = {1000, 1000, -2000};

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

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count()) // accounts: node, treasury, payer, new crypto, proxy
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)
                , () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );
    }

    @Test
    void cryptoCreateTransactionWithBody() throws Exception {
        Transaction transaction = createTransactionWithBody();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Entities dbNewAccountEntity = getEntity(dbTransaction.getEntityId());
        CryptoTransfer dbCryptoTransfer = cryptoTransferRepository.findByConsensusTimestampAndEntityNum(
                Utility.timeStampInNanos(record.getConsensusTimestamp()),
                dbNewAccountEntity.getEntityNum()).get();
        AccountID recordReceiptAccountId = record.getReceipt().getAccountID();

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)
                , () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp())

                // transaction body inputs
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())

                // Crypto transfer list
                , () -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()),
                        dbCryptoTransfer.getConsensusTimestamp())
                , () -> assertEquals(recordReceiptAccountId.getRealmNum(), dbCryptoTransfer.getRealmNum())
                , () -> assertEquals(recordReceiptAccountId.getAccountNum(), dbCryptoTransfer.getEntityNum())
        );
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

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(3, entityRepository.count())
                , () -> assertEquals(3, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
                , () -> assertNull(dbTransaction.getEntityId())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );
    }

    @Test
    void cryptoCreateInitialBalanceInTransferList() throws Exception {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord tempRecord = transactionRecordSuccess(transactionBody);

        // add initial balance to transfer list
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();

        TransferList.Builder transferList = tempRecord.getTransferList().toBuilder()
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(accountId).setAmount(initialBalance))
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-initialBalance));
        TransactionRecord record = tempRecord.toBuilder().setTransferList(transferList).build();

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)
                , () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );
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

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(10, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)
                , () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp())
        );
    }

    @Test
    void cryptoUpdateSuccessfulTransaction() throws Exception {
        createAccount();

        // now update
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoUpdateTransactionBody cryptoUpdateTransactionBody = transactionBody.getCryptoUpdateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entities dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(6, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // transaction body inputs
                , () -> assertEquals(cryptoUpdateTransactionBody.getAutoRenewPeriod().getSeconds(),
                        dbAccountEntity.getAutoRenewPeriod())
                , () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(
                        cryptoUpdateTransactionBody.getKey().toByteArray()), dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertAccount(cryptoUpdateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoUpdateTransactionBody.getKey().toByteArray(), dbAccountEntity.getKey())
                , () -> assertEquals(Utility.timeStampInNanos(cryptoUpdateTransactionBody.getExpirationTime()),
                        dbAccountEntity.getExpiryTimeNs())
        );
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
        createAccount();

        // now update
        var updateTransaction = buildTransaction(builder -> {
            builder.getCryptoUpdateAccountBuilder()
                    .setAccountIDToUpdate(accountId)
                    // *** THIS IS THE OVERFLOW WE WANT TO TEST ***
                    // This should result in the entity having a Long.MAX_VALUE or Long.MIN_VALUE expirations
                    // (the results of overflows).
                    .setExpirationTime(Timestamp.newBuilder().setSeconds(seconds));
        });
        var record = transactionRecordSuccess(TransactionBody.parseFrom(updateTransaction.getBodyBytes()));

        parseRecordItemAndCommit(new RecordItem(updateTransaction, record));

        var dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpiryTimeNs())
        );
    }

    @Test
    void cryptoUpdateFailedTransaction() throws Exception {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionRecord createRecord = transactionRecordSuccess(
                TransactionBody.parseFrom(createTransaction.getBodyBytes()));
        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now update
        Transaction transaction = cryptoUpdateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntityBefore = getTransactionEntity(createRecord.getConsensusTimestamp());
        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
                // receipt
                , () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity)
                // no changes to entity
                , () -> assertEquals(dbAccountEntityBefore, dbAccountEntity)
        );
    }

    @Test
    void cryptoDeleteSuccessfulTransaction() throws Exception {
        // first create the account
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entities dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, true)

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpiryTimeNs())
        );
    }

    @Test
    void cryptoDeleteFailedTransaction() throws Exception {
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entities dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getEd25519PublicKeyHex())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpiryTimeNs())
        );
    }

    @Test
    void cryptoAddLiveHashPersist() throws Exception {
        // create the account and add live hash
        createAccount();
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Entities dbAccountEntity = getEntity(dbTransaction.getEntityId());
        LiveHash dbLiveHash = liveHashRepository.findById(dbTransaction.getConsensusNs()).get();

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(1, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // transaction body inputs
                , () -> assertAccount(cryptoAddLiveHashTransactionBody.getLiveHash().getAccountId(), dbAccountEntity)
                // TODO (issue #303) cryptoAddClaimTransactionBody.getClaim().getClaimDuration()
                , () -> assertArrayEquals(cryptoAddLiveHashTransactionBody.getLiveHash().getHash()
                        .toByteArray(), dbLiveHash
                        .getLivehash())
        );
    }

    @Test
    void cryptoAddLiveHashDoNotPersist() throws Exception {
        entityProperties.getPersist().setClaims(false);
        // create the account and add live hash
        createAccount();
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // transaction body inputs
                , () -> assertAccount(cryptoAddLiveHashTransactionBody.getLiveHash().getAccountId(), dbAccountEntity)
        );
    }

    @Test
    void cryptoDeleteLiveHash() throws Exception {
        // create the account and add live hash
        createAccount();
        Transaction transactionAddLiveHash = cryptoAddLiveHashTransaction();
        parseRecordItemAndCommit(new RecordItem(transactionAddLiveHash,
                transactionRecordSuccess(TransactionBody.parseFrom(transactionAddLiveHash.getBodyBytes()))));

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        CryptoDeleteLiveHashTransactionBody deleteLiveHashTransactionBody = transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entities dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(3, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(11, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(1, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // transaction body inputs
                , () -> assertAccount(deleteLiveHashTransactionBody.getAccountOfLiveHash(), dbAccountEntity)
                // TODO (issue #303) check deleted
        );
    }

    @Test
    void cryptoTransferWithPersistence() throws Exception {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(5, entityRepository.count())
                , () -> assertEquals(6, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void cryptoTransferWithoutPersistence() throws Exception {
        entityProperties.getPersist().setCryptoTransferAmounts(false);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEquals(2, entityRepository.count())
                , () -> assertEquals(0, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
        );

        // Crypto transfer list
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

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(3, entityRepository.count(), "Payer, node and treasury"),
                () -> assertEquals(3, cryptoTransferRepository.count(), "Node and network fee"),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void unknownTransactionResult() throws Exception {
        int unknownResult = -1000;
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        TransactionRecord record = transactionRecord(transactionBody, unknownResult);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        org.assertj.core.api.Assertions.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .extracting(com.hedera.mirror.importer.domain.Transaction::getResult)
                .containsOnly(unknownResult);
    }

    private void createAccount() throws Exception {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = TransactionBody.parseFrom(createTransaction.getBodyBytes());
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);
        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));
    }

    private void assertCryptoTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Entities actualAccount = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertAccount(record.getReceipt().getAccountID(), actualAccount),
                () -> assertEquals(deleted, actualAccount.isDeleted()));
    }

    private void assertCryptoEntity(CryptoCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entities actualAccount = getTransactionEntity(consensusTimestamp);
        Entities actualProxyAccountId = getEntity(actualAccount.getProxyAccountId());
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualAccount.getAutoRenewPeriod()),
                () -> assertEquals(Utility.protobufKeyToHexIfEd25519OrNull(expected.getKey().toByteArray()),
                        actualAccount.getEd25519PublicKeyHex()),
                () -> assertArrayEquals(expected.getKey().toByteArray(), actualAccount.getKey()),
                () -> assertNull(actualAccount.getExpiryTimeNs()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccountId),
                () -> assertNull(actualAccount.getExpiryTimeNs())
        );
    }

    private TransactionRecord transactionRecordSuccess(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum responseCode) {
        return transactionRecord(transactionBody, responseCode.getNumber());
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int status) {
        return buildTransactionRecord(recordBuilder -> recordBuilder.getReceiptBuilder().setAccountID(accountId),
                transactionBody, status);
    }

    private Transaction cryptoCreateTransaction() {
        return buildTransaction(builder -> builder.getCryptoCreateAccountBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(1000L)
                .setKey(keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"))
                .setNewRealmAdminKey(keyFromString("0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"))
                .setProxyAccountID(AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3))
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiveRecordThreshold(2000L)
                .setReceiverSigRequired(true)
                .setSendRecordThreshold(3000L));
    }

    private Transaction createTransactionWithBody() {
        return buildTransaction(builder -> builder.getCryptoCreateAccountBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(1000L)
                .setKey(keyFromString(KEY))
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setProxyAccountID(AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3))
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiveRecordThreshold(2000L)
                .setReceiverSigRequired(true)
                .setSendRecordThreshold(3000L));
    }

    private Transaction cryptoUpdateTransaction() {
        return buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountId)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(5001L))
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(keyFromString(KEY))
                .setProxyAccountID(AccountID.newBuilder().setShardNum(5).setRealmNum(6).setAccountNum(8))
                .setReceiveRecordThreshold(5001L)
                .setReceiverSigRequired(false)
                .setSendRecordThreshold(6001L));
    }

    private Transaction cryptoDeleteTransaction() {
        return buildTransaction(builder -> builder.getCryptoDeleteBuilder()
                .setDeleteAccountID(accountId));
    }

    private Transaction cryptoAddLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoAddLiveHashBuilder()
                .getLiveHashBuilder()
                .setAccountId(accountId)
                .setDuration(Duration.newBuilder().setSeconds(10000L))
                .setHash(ByteString.copyFromUtf8("live hash"))
                .setKeys(KeyList.newBuilder().addKeys(keyFromString(KEY))));
    }

    private Transaction cryptoDeleteLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoDeleteLiveHashBuilder()
                .setAccountOfLiveHash(accountId)
                .setLiveHashToDelete(ByteString.copyFromUtf8("live hash")));
    }

    private Transaction cryptoTransferTransaction() {
        return buildTransaction(builder -> {
            for (int i = 0; i < additionalTransfers.length; i++) {
                builder.getCryptoTransferBuilder().getTransfersBuilder()
                        .addAccountAmounts(accountAmount(additionalTransfers[i],additionalTransferAmounts[i]));
            }
        });
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
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertArrayEquals(expectedBytes, dbTransaction.getTransactionBytes());
    }
}
