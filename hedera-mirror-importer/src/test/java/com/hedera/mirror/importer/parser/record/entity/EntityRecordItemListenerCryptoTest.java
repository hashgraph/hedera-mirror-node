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

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
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
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

class EntityRecordItemListenerCryptoTest extends AbstractEntityRecordItemListenerTest {
    private static final long INITIAL_BALANCE = 1000L;
    private static final AccountID accountId = AccountID.newBuilder().setAccountNum(1001).build();
    private static final long[] additionalTransfers = {5000, 6000};
    private static final long[] additionalTransferAmounts = {1001, 1002};

    @BeforeEach
    void before() {
        entityProperties.getPersist().setClaims(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void cryptoCreate() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var accountEntityId = EntityId.of(accountId);
        var consensusTimestamp = Utility.timeStampInNanos(record.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(new CryptoTransfer.Id(
                INITIAL_BALANCE, consensusTimestamp, accountEntityId));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(accountEntityId, EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)
                , () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp())
                , () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
                , () -> assertThat(initialBalanceTransfer).isPresent()
        );
    }

    @Test
    void cryptoCreateFailedTransaction() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
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
                , () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY))
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
    void cryptoCreateInitialBalanceInTransferList() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
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
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
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
    void cryptoUpdateSuccessfulTransaction() {
        createAccount();

        // now update
        Transaction transaction = cryptoUpdateTransaction(accountId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoUpdateTransactionBody cryptoUpdateTransactionBody = transactionBody.getCryptoUpdateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));
        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entity dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY), EntityId.of(PROXY_UPDATE))
                , () -> assertEquals(8, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // transaction body inputs
                , () -> assertEquals(cryptoUpdateTransactionBody.getAutoRenewPeriod().getSeconds(),
                        dbAccountEntity.getAutoRenewPeriod())
                , () -> assertEquals(Utility.convertSimpleKeyToHex(
                        cryptoUpdateTransactionBody.getKey().toByteArray()), dbAccountEntity.getPublicKey())
                , () -> assertAccount(cryptoUpdateTransactionBody.getProxyAccountID(), dbProxyAccountId)
                , () -> assertArrayEquals(cryptoUpdateTransactionBody.getKey().toByteArray(), dbAccountEntity.getKey())
                , () -> assertEquals(cryptoUpdateTransactionBody.getMemo().getValue(), dbAccountEntity.getMemo())
                , () -> assertEquals(Utility.timeStampInNanos(cryptoUpdateTransactionBody.getExpirationTime()),
                        dbAccountEntity.getExpirationTimestamp())
                , () -> assertEquals(Utility.timestampInNanosMax(record.getConsensusTimestamp()),
                        dbAccountEntity.getModifiedTimestamp())
                , () -> assertFalse(dbAccountEntity.getReceiverSigRequired())
        );
    }

    /**
     * Github issue #483
     */
    @Test
    void samePayerAndUpdateAccount() {
        Transaction transaction = cryptoUpdateTransaction(accountId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        transactionBody = TransactionBody.newBuilder()
                .mergeFrom(transactionBody)
                .setTransactionID(Utility.getTransactionId(accountId))
                .build();
        transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBody.toByteString())
                        .build().toByteString())
                .build();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(transactionRepository.findById(Utility.timestampInNanosMax(record.getConsensusTimestamp())))
                .get()
                .extracting(com.hedera.mirror.importer.domain.Transaction::getPayerAccountId,
                        com.hedera.mirror.importer.domain.Transaction::getEntityId)
                .containsOnly(EntityId.of(accountId));
    }

    // Transactions in production have proxyAccountID explicitly set to '0.0.0'. Test is to prevent code regression
    // in handling this weird case.
    @Test
    void proxyAccountIdSetTo0() {
        // given
        Transaction transaction = cryptoUpdateTransaction(accountId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        var bodyBuilder = transactionBody.toBuilder();
        bodyBuilder.getCryptoUpdateAccountBuilder().setProxyAccountID(AccountID.getDefaultInstance());
        transactionBody = bodyBuilder.build();
        transaction = Transaction.newBuilder().setSignedTransactionBytes(SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .build().toByteString())
                .build();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        // then: process the transaction without throwing NPE
        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(transactionRepository.count()).isEqualTo(1L);
        assertThat(entityRepository.findById(EntityId.of(accountId).getId()))
                .get()
                .extracting(Entity::getProxyAccountId)
                .isNull();
    }

    @DisplayName("update account such that expiration timestamp overflows nanos_timestamp")
    @ParameterizedTest(name = "with seconds {0} and expectedNanosTimestamp {1}")
    @CsvSource({
            "9223372036854775807, 9223372036854775807",
            "31556889864403199, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808",
            "-1000000000000000000, -9223372036854775808"
    })
    void cryptoUpdateExpirationOverflow(long seconds, long expectedNanosTimestamp) {
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
        var record = transactionRecordSuccess(getTransactionBody(updateTransaction));

        parseRecordItemAndCommit(new RecordItem(updateTransaction, record));

        var dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpirationTimestamp())
        );
    }

    @Test
    void cryptoUpdateFailedTransaction() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionRecord createRecord = transactionRecordSuccess(
                getTransactionBody(createTransaction));
        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));

        // now update
        Transaction transaction = cryptoUpdateTransaction(accountId);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntityBefore = getTransactionEntity(createRecord.getConsensusTimestamp());
        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
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
    void cryptoDeleteSuccessfulTransaction() {
        // first create the account
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entity dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, true)

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getPublicKey())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpirationTimestamp())
        );
    }

    @Test
    void cryptoDeleteFailedTransaction() {
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        Entity dbProxyAccountId = getEntity(dbAccountEntity.getProxyAccountId());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(8, cryptoTransferRepository.count()) // 3 + 3 fee transfers + 2 for initial balance
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertCryptoTransaction(transactionBody, record, false)

                // Additional entity checks
                , () -> assertNotNull(dbAccountEntity.getPublicKey())
                , () -> assertNotNull(dbAccountEntity.getKey())
                , () -> assertNotNull(dbAccountEntity.getAutoRenewPeriod())
                , () -> assertNotNull(dbProxyAccountId)
                , () -> assertNull(dbAccountEntity.getExpirationTimestamp())
        );
    }

    @Test
    void cryptoAddLiveHashPersist() {
        // create the account and add live hash
        createAccount();
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Entity dbAccountEntity = getEntity(dbTransaction.getEntityId());
        LiveHash dbLiveHash = liveHashRepository.findById(dbTransaction.getConsensusTimestamp()).get();

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
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
    void cryptoAddLiveHashDoNotPersist() {
        entityProperties.getPersist().setClaims(false);
        // create the account and add live hash
        createAccount();
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
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
    void cryptoDeleteLiveHash() {
        // create the account and add live hash
        createAccount();
        Transaction transactionAddLiveHash = cryptoAddLiveHashTransaction();
        parseRecordItemAndCommit(new RecordItem(transactionAddLiveHash,
                transactionRecordSuccess(getTransactionBody(transactionAddLiveHash))));

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoDeleteLiveHashTransactionBody deleteLiveHashTransactionBody = transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(3, transactionRepository.count())
                , () -> assertEntities(EntityId.of(accountId), EntityId.of(PROXY), EntityId.of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
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
    void cryptoTransferWithPersistence() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count())
                , () -> assertEntities(EntityId
                        .of(String.format("0.0.%d", additionalTransfers[0]), EntityTypeEnum.ACCOUNT), EntityId
                        .of(String.format("0.0.%d", additionalTransfers[1]), EntityTypeEnum.ACCOUNT), EntityId
                        .of(PAYER), EntityId
                        .of(NODE), EntityId.of(TREASURY))
                , () -> assertEquals(5, cryptoTransferRepository.count())
                , () -> assertEquals(0, contractResultRepository.count())
                , () -> assertEquals(0, liveHashRepository.count())
                , () -> assertEquals(0, fileDataRepository.count())
                , () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void cryptoTransferWithoutPersistence() {
        entityProperties.getPersist().setCryptoTransferAmounts(false);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE)),
                () -> assertEquals(2, entityRepository.count()),
                () -> assertEquals(0, cryptoTransferRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void cryptoTransferFailedTransaction() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INVALID_ACCOUNT_ID);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(PAYER), EntityId.of(NODE), EntityId.of(TREASURY)),
                () -> assertEquals(3, cryptoTransferRepository.count(), "Node and network fee"),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void unknownTransactionResult() {
        int unknownResult = -1000;
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, unknownResult);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .extracting(com.hedera.mirror.importer.domain.Transaction::getResult)
                .containsOnly(unknownResult);
    }

    private void createAccount() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);
        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));
    }

    private void assertCryptoTransaction(TransactionBody transactionBody, TransactionRecord record, boolean deleted) {
        Entity actualAccount = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertAccount(record.getReceipt().getAccountID(), actualAccount),
                () -> assertEntity(actualAccount));
    }

    private void assertCryptoEntity(CryptoCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualAccount = getTransactionEntity(consensusTimestamp);
        Entity actualProxyAccountId = getEntity(actualAccount.getProxyAccountId());
        long timestamp = Utility.timestampInNanosMax(consensusTimestamp);
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualAccount.getAutoRenewPeriod()),
                () -> assertEquals(timestamp, actualAccount.getCreatedTimestamp()),
                () -> assertEquals(Utility.convertSimpleKeyToHex(expected.getKey().toByteArray()),
                        actualAccount.getPublicKey()),
                () -> assertArrayEquals(expected.getKey().toByteArray(), actualAccount.getKey()),
                () -> assertEquals(expected.getMemo(), actualAccount.getMemo()),
                () -> assertNull(actualAccount.getExpirationTimestamp()),
                () -> assertAccount(expected.getProxyAccountID(), actualProxyAccountId),
                () -> assertEquals(timestamp, actualAccount.getModifiedTimestamp())
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

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int status, int accountNum) {
        return buildTransactionRecord(
                recordBuilder -> recordBuilder
                        .getReceiptBuilder()
                        .setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(accountNum)),
                transactionBody,
                status);
    }

    private Transaction cryptoCreateTransaction() {
        return buildTransaction(builder -> builder.getCryptoCreateAccountBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(INITIAL_BALANCE)
                .setKey(keyFromString(KEY))
                .setMemo("CryptoCreateAccount memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setProxyAccountID(PROXY)
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiverSigRequired(true));
    }

    private Transaction cryptoUpdateTransaction(AccountID accountNum) {
        return buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountNum)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(keyFromString(KEY))
                .setMemo(StringValue.of("CryptoUpdateAccount memo"))
                .setProxyAccountID(PROXY_UPDATE)
                .setReceiverSigRequiredWrapper(BoolValue.of(false)));
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
                        .addAccountAmounts(accountAmount(additionalTransfers[i], additionalTransferAmounts[i]));
            }
        });
    }

    @Test
    void cryptoTransferPersistRawBytesDefault() {
        // Use the default properties for record parsing - the raw bytes should NOT be stored in the db
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    @Test
    void cryptoTransferPersistRawBytesTrue() {
        // Explicitly persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(true);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, transaction.toByteArray());
    }

    @Test
    void cryptoTransferPersistRawBytesFalse() {
        // Explicitly DO NOT persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(false);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    private void testRawBytes(Transaction transaction, byte[] expectedBytes) {
        // given
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        // when
        parseRecordItemAndCommit(new RecordItem(transaction.toByteArray(), record.toByteArray()));

        // then
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertArrayEquals(expectedBytes, dbTransaction.getTransactionBytes());
    }
}
