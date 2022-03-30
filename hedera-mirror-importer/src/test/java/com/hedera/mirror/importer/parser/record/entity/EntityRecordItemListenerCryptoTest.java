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

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.assertj.core.api.Condition;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.CryptoAllowanceRepository;
import com.hedera.mirror.importer.repository.NftAllowanceRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import com.hedera.mirror.importer.util.Utility;

class EntityRecordItemListenerCryptoTest extends AbstractEntityRecordItemListenerTest {
    private static final long INITIAL_BALANCE = 1000L;
    private static final AccountID accountId1 = AccountID.newBuilder().setAccountNum(1001).build();
    private static final long[] additionalTransfers = {5000};
    private static final long[] additionalTransferAmounts = {1001, 1002};
    private static final ByteString ALIAS_KEY = ByteString.copyFromUtf8(
            "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff");

    @Resource
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Resource
    private NftAllowanceRepository nftAllowanceRepository;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private TokenAllowanceRepository tokenAllowanceRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setClaims(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setTransactionBytes(false);
    }

    @Test
    void cryptoAdjustAllowance() {
        RecordItem recordItem = recordItemBuilder.cryptoAdjustAllowance().build();
        parseRecordItemAndCommit(recordItem);
        assertAllowances(recordItem);
    }

    @Test
    void cryptoApproveAllowance() {
        RecordItem recordItem = recordItemBuilder.cryptoApproveAllowance().build();
        parseRecordItemAndCommit(recordItem);
        assertAllowances(recordItem);
    }

    @Test
    void cryptoCreateWithInitialBalance() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();

        var transfer1 = accountAmount(accountId1.getAccountNum(), initialBalance);
        var transfer2 = accountAmount(PAYER.getAccountNum(), -initialBalance);
        TransactionRecord record = transactionRecordSuccess(transactionBody, recordBuilder ->
                groupCryptoTransfersByAccountId(recordBuilder, List.of(transfer1, transfer2)));

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        final var accountEntityId = EntityId.of(accountId1);
        final var consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        final var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        final Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(new CryptoTransfer.Id(
                initialBalance, consensusTimestamp, accountEntityId.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountEntityId),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isPresent()
        );
    }

    @Test
    void cryptoCreateWithZeroInitialBalance() {
        final long initialBalance = 0;
        CryptoCreateTransactionBody.Builder cryptoCreateBuilder = cryptoCreateAccountBuilderWithDefaults()
                .setInitialBalance(initialBalance);
        Transaction transaction = cryptoCreateTransaction(cryptoCreateBuilder);
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        final var accountEntityId = EntityId.of(accountId1);
        final var consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        final var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        final Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(new CryptoTransfer.Id(
                initialBalance, consensusTimestamp, accountEntityId.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountEntityId),
                () -> assertCryptoTransfers(3),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isEmpty()
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
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );
    }

    @Test
    void cryptoCreateInitialBalanceInTransferList() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();

        // add initial balance to transfer list
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();
        var transfer1 = accountAmount(accountId1.getAccountNum(), initialBalance);
        var transfer2 = accountAmount(PAYER.getAccountNum(), -initialBalance);
        TransactionRecord record = transactionRecordSuccess(transactionBody, recordBuilder ->
                groupCryptoTransfersByAccountId(recordBuilder, List.of(transfer1, transfer2))
        );

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance())
        );
    }

    @Test
    void cryptoCreateAccountAlias() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = buildTransactionRecord(
                recordBuilder -> recordBuilder.setAlias(ALIAS_KEY).getReceiptBuilder().setAccountID(accountId1),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var accountEntityId = EntityId.of(accountId1);
        var consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(new CryptoTransfer.Id(
                0, consensusTimestamp, accountEntityId.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountEntityId),
                () -> assertCryptoTransfers(3),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, record.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isEmpty(),
                () -> assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray())).get()
                        .isEqualTo(accountEntityId.getId())
        );
    }

    @Test
    void cryptoUpdateSuccessfulTransaction() {
        createAccount();

        // now update
        Transaction transaction = cryptoUpdateTransaction(accountId1);
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoUpdateTransactionBody cryptoUpdateTransactionBody = transactionBody.getCryptoUpdateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));
        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertCryptoTransaction(transactionBody, record),

                // transaction body inputs
                () -> assertEquals(cryptoUpdateTransactionBody.getAutoRenewPeriod().getSeconds(),
                        dbAccountEntity.getAutoRenewPeriod()),
                () -> assertEquals(DomainUtils.getPublicKey(
                        cryptoUpdateTransactionBody.getKey().toByteArray()), dbAccountEntity.getPublicKey()),
                () -> assertEquals(EntityId.of(cryptoUpdateTransactionBody.getProxyAccountID()),
                        dbAccountEntity.getProxyAccountId()),
                () -> assertArrayEquals(cryptoUpdateTransactionBody.getKey()
                        .toByteArray(), dbAccountEntity.getKey()),
                () -> assertEquals(cryptoUpdateTransactionBody.getMaxAutomaticTokenAssociations().getValue(),
                        dbAccountEntity.getMaxAutomaticTokenAssociations()),
                () -> assertEquals(cryptoUpdateTransactionBody.getMemo().getValue(), dbAccountEntity.getMemo()),
                () -> assertEquals(DomainUtils.timeStampInNanos(cryptoUpdateTransactionBody.getExpirationTime()),
                        dbAccountEntity.getExpirationTimestamp()),
                () -> assertEquals(DomainUtils.timestampInNanosMax(record.getConsensusTimestamp()),
                        dbAccountEntity.getTimestampLower()),
                () -> assertFalse(dbAccountEntity.getReceiverSigRequired())
        );
    }

    /**
     * Github issue #483
     */
    @Test
    void samePayerAndUpdateAccount() {
        Transaction transaction = cryptoUpdateTransaction(accountId1);
        TransactionBody transactionBody = getTransactionBody(transaction);
        transactionBody = TransactionBody.newBuilder()
                .mergeFrom(transactionBody)
                .setTransactionID(Utility.getTransactionId(accountId1))
                .build();
        transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBody.toByteString())
                        .build().toByteString())
                .build();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertThat(transactionRepository.findById(DomainUtils.timestampInNanosMax(record.getConsensusTimestamp())))
                .get()
                .extracting(com.hedera.mirror.common.domain.transaction.Transaction::getPayerAccountId,
                        com.hedera.mirror.common.domain.transaction.Transaction::getEntityId)
                .containsOnly(EntityId.of(accountId1));
    }

    // Transactions in production have proxyAccountID explicitly set to '0.0.0'. Test is to prevent code regression
    // in handling this weird case.
    @Test
    void proxyAccountIdSetTo0() {
        // given
        Transaction transaction = cryptoUpdateTransaction(accountId1);
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
        assertThat(entityRepository.findById(EntityId.of(accountId1).getId()))
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
                    .setAccountIDToUpdate(accountId1)
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
        Transaction transaction = cryptoUpdateTransaction(accountId1);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntityBefore = getTransactionEntity(createRecord.getConsensusTimestamp());
        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with one transfer per account
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertAccount(record.getReceipt().getAccountID(), dbAccountEntity),
                () -> assertEquals(dbAccountEntityBefore, dbAccountEntity)// no changes to entity
        );
    }

    @Test
    void cryptoDeleteSuccessfulTransaction() {
        // first create the account
        createAccount();
        Entity dbAccountEntityBefore = getEntity(EntityId.of(accountId1));

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with one transfer per account
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertThat(dbAccountEntity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(DomainUtils.timestampInNanosMax(record.getConsensusTimestamp()),
                                Entity::getTimestampLower)
                        .usingRecursiveComparison()
                        .ignoringFields("deleted", "timestampRange")
                        .isEqualTo(dbAccountEntityBefore)
        );
    }

    @Test
    void cryptoDeleteFailedTransaction() {
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE.getNumber(),
                recordBuilder -> groupCryptoTransfersByAccountId(recordBuilder, List.of()));

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        Entity dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with only one transfer per account
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertThat(dbAccountEntity)
                        .isNotNull()
                        .returns(false, Entity::getDeleted)
        );
    }

    @Test
    void cryptoAddLiveHashPersist() {
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        LiveHash dbLiveHash = liveHashRepository.findById(dbTransaction.getConsensusTimestamp()).get();

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertEquals(1, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertArrayEquals(cryptoAddLiveHashTransactionBody.getLiveHash().getHash().toByteArray(),
                        dbLiveHash.getLivehash())
        );
    }

    @Test
    void cryptoAddLiveHashDoNotPersist() {
        entityProperties.getPersist().setClaims(false);
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void cryptoDeleteLiveHash() {
        Transaction transactionAddLiveHash = cryptoAddLiveHashTransaction();
        parseRecordItemAndCommit(new RecordItem(transactionAddLiveHash,
                transactionRecordSuccess(getTransactionBody(transactionAddLiveHash))));

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoDeleteLiveHashTransactionBody deleteLiveHashTransactionBody = transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(6),
                () -> assertEquals(1, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
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
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
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
                () -> assertEntities(),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertCryptoTransfers(0),
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
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void cryptoTransferFailedTransactionErrata() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = buildTransactionRecord(r -> {
            for (int i = 0; i < additionalTransfers.length; i++) {
                // Add non-fee transfers to record
                var accountAmount = accountAmount(additionalTransfers[i], additionalTransferAmounts[i]);
                r.getTransferListBuilder().addAccountAmounts(accountAmount);
            }
        }, transactionBody, ResponseCodeEnum.INVALID_ACCOUNT_ID.getNumber());

        var recordItem = new RecordItem(transaction, record);
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count(), "Node, network fee & errata"),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> {
                    for (int i = 0; i < additionalTransfers.length; i++) {
                        var id = new CryptoTransfer.Id(additionalTransferAmounts[i],
                                recordItem.getConsensusTimestamp(), additionalTransfers[i]);
                        assertThat(cryptoTransferRepository.findById(id))
                                .get()
                                .extracting(CryptoTransfer::getErrata)
                                .isEqualTo(ErrataType.DELETE);
                    }
                }
        );
    }

    @Test
    void cryptoTransferWithAlias() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setNonFeeTransfers(true);
        Entity entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray())).isNotPresent();

        // Crypto create alias account
        Transaction accountCreateTransaction = cryptoCreateTransaction();
        TransactionBody accountCreateTransactionBody = getTransactionBody(accountCreateTransaction);
        TransactionRecord recordCreate = buildTransactionRecord(
                recordBuilder -> recordBuilder.setAlias(ALIAS_KEY).getReceiptBuilder().setAccountID(accountId1),
                accountCreateTransactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        var transfer1 = accountAliasAmount(ALIAS_KEY, 1003).build();
        var transfer2 = accountAliasAmount(ByteString.copyFrom(entity.getAlias()), 1004).build();
        // Crypto transfer to both existing alias and newly created alias accounts
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder().getTransfersBuilder()
                .addAccountAmounts(transfer1)
                .addAccountAmounts(transfer2));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord recordTransfer = transactionRecordSuccess(transactionBody,
                builder -> groupCryptoTransfersByAccountId(builder, List.of()));

        parseRecordItemsAndCommit(List.of(new RecordItem(accountCreateTransaction, recordCreate),
                new RecordItem(transaction, recordTransfer)));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1), entity.toEntityId()),
                () -> assertCryptoTransfers(6)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2)),
                () -> assertEquals(additionalTransfers.length * 2 + 2, nonFeeTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, recordTransfer),
                () -> assertThat(findNonFeeTransfers())
                        .extracting(NonFeeTransfer::getEntityId)
                        .extracting(EntityId::getEntityNum)
                        .contains(accountId1.getAccountNum(), entity.getNum())
        );
    }

    private Condition<CryptoTransfer> isAccountAmountReceiverAccountAmount(AccountAmount receiver) {
        return new Condition<>(
                cryptoTransfer ->
                        isAccountAmountReceiverAccountAmount(cryptoTransfer, receiver),
                format("Is %s the receiver account amount.", receiver));
    }

    @ParameterizedTest
    @EnumSource(value = PartialDataAction.class, names = {"DEFAULT", "SKIP"})
    void cryptoTransferWithUnknownAlias(PartialDataAction partialDataAction) {
        // given
        // both accounts have alias, and only account2's alias is in db
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setNonFeeTransfers(true);
        parserProperties.setPartialDataAction(partialDataAction);

        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().persist();

        // crypto transfer from unknown account1 alias to account2 alias
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder().getTransfersBuilder()
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account1.getAlias()), 100))
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account2.getAlias()), -100)));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(r -> r.getTransferListBuilder()
                        .addAccountAmounts(accountAmount(account1.getNum(), 100))
                        .addAccountAmounts(accountAmount(account2.getNum(), -100)),
                transactionBody, ResponseCodeEnum.SUCCESS.getNumber());
        List<EntityId> expectedEntityIds = partialDataAction == PartialDataAction.DEFAULT ?
                Arrays.asList(account2.toEntityId(), null) : List.of(account2.toEntityId());

        // when
        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        // then
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(5, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, transactionRecord),
                () -> assertThat(findNonFeeTransfers())
                        .extracting(NonFeeTransfer::getEntityId)
                        .containsExactlyInAnyOrderElementsOf(expectedEntityIds)
        );
    }

    @Test
    void cryptoTransferWithUnknownAliasActionError() {
        // given
        // both accounts have alias, and only account2's alias is in db
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setNonFeeTransfers(true);
        parserProperties.setPartialDataAction(PartialDataAction.ERROR);

        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().persist();

        // crypto transfer from unknown account1 alias to account2 alias
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder().getTransfersBuilder()
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account1.getAlias()), 100))
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account2.getAlias()), -100)));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(r -> r.getTransferListBuilder()
                        .addAccountAmounts(accountAmount(account1.getNum(), 100))
                        .addAccountAmounts(accountAmount(account2.getNum(), -100)),
                transactionBody, ResponseCodeEnum.SUCCESS.getNumber());

        // when, then
        assertThrows(AliasNotFoundException.class,
                () -> parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord)));
        assertAll(
                () -> assertEquals(0, transactionRepository.count()),
                () -> assertEquals(0, cryptoTransferRepository.count()),
                () -> assertThat(findNonFeeTransfers()).isEmpty()
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
                .extracting(com.hedera.mirror.common.domain.transaction.Transaction::getResult)
                .containsOnly(unknownResult);
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

    private void assertAllowances(RecordItem recordItem) {
        assertAll(
                () -> assertEquals(1, cryptoAllowanceRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(2, nftAllowanceRepository.count()),
                () -> assertEquals(1, tokenAllowanceRepository.count()),
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getRecord()),
                () -> assertThat(cryptoAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getAmount()).isPositive())
                        .allSatisfy(a -> assertThat(a.getOwner()).isPositive())
                        .allSatisfy(a -> assertThat(a.getSpender()).isPositive())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(nftAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getOwner()).isPositive())
//                        .allSatisfy(a -> assertThat(a.getSerialNumbers()).isNotNull())
                        .allSatisfy(a -> assertThat(a.getSpender()).isPositive())
                        .allSatisfy(a -> assertThat(a.getTokenId()).isPositive())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(tokenAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getAmount()).isPositive())
                        .allSatisfy(a -> assertThat(a.getOwner()).isPositive())
                        .allSatisfy(a -> assertThat(a.getSpender()).isPositive())
                        .allSatisfy(a -> assertThat(a.getTokenId()).isPositive())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId()))
        );
    }

    private void assertCryptoTransaction(TransactionBody transactionBody, TransactionRecord record) {
        Entity actualAccount = getTransactionEntity(record.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, record),
                () -> assertAccount(record.getReceipt().getAccountID(), actualAccount),
                () -> assertEntity(actualAccount));
    }

    private void assertCryptoEntity(CryptoCreateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity actualAccount = getTransactionEntity(consensusTimestamp);
        long timestamp = DomainUtils.timestampInNanosMax(consensusTimestamp);
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualAccount.getAutoRenewPeriod()),
                () -> assertEquals(timestamp, actualAccount.getCreatedTimestamp()),
                () -> assertEquals(false, actualAccount.getDeleted()),
                () -> assertNull(actualAccount.getExpirationTimestamp()),
                () -> assertArrayEquals(expected.getKey().toByteArray(), actualAccount.getKey()),
                () -> assertEquals(0, actualAccount.getMaxAutomaticTokenAssociations()),
                () -> assertEquals(expected.getMemo(), actualAccount.getMemo()),
                () -> assertEquals(timestamp, actualAccount.getTimestampLower()),
                () -> assertEquals(DomainUtils.getPublicKey(expected.getKey().toByteArray()),
                        actualAccount.getPublicKey()),
                () -> assertEquals(EntityId.of(expected.getProxyAccountID()),
                        actualAccount.getProxyAccountId()),
                () -> assertEquals(expected.getReceiverSigRequired(), actualAccount.getReceiverSigRequired())
        );
    }

    protected IterableAssert<CryptoTransfer> assertCryptoTransfers(
            int expectedNumberOfCryptoTransfers) {
        return assertThat(
                cryptoTransferRepository.findAll())
                .hasSize(expectedNumberOfCryptoTransfers)
                .allSatisfy(a -> assertThat(a.getId().getAmount()).isNotZero());
    }

    private void createAccount() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);
        parseRecordItemAndCommit(new RecordItem(createTransaction, createRecord));
    }

    private CryptoCreateTransactionBody.Builder cryptoCreateAccountBuilderWithDefaults() {
        return CryptoCreateTransactionBody.newBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(INITIAL_BALANCE)
                .setKey(keyFromString(KEY))
                .setMemo("CryptoCreateAccount memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setProxyAccountID(PROXY)
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiverSigRequired(true);
    }

    private Transaction cryptoCreateTransaction() {
        return cryptoCreateTransaction(cryptoCreateAccountBuilderWithDefaults());
    }

    private Transaction cryptoCreateTransaction(CryptoCreateTransactionBody.Builder cryptoCreateBuilder) {
        return buildTransaction(builder -> builder.setCryptoCreateAccount(cryptoCreateBuilder));
    }

    private Transaction cryptoAddLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoAddLiveHashBuilder()
                .getLiveHashBuilder()
                .setAccountId(accountId1)
                .setDuration(Duration.newBuilder().setSeconds(10000L))
                .setHash(ByteString.copyFromUtf8("live hash"))
                .setKeys(KeyList.newBuilder().addKeys(keyFromString(KEY))));
    }

    private Transaction cryptoDeleteLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoDeleteLiveHashBuilder()
                .setAccountOfLiveHash(accountId1)
                .setLiveHashToDelete(ByteString.copyFromUtf8("live hash")));
    }

    private Transaction cryptoDeleteTransaction() {
        return buildTransaction(builder -> builder.getCryptoDeleteBuilder()
                .setDeleteAccountID(accountId1));
    }

    private Transaction cryptoUpdateTransaction(AccountID accountNum) {
        return buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountNum)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(keyFromString(KEY))
                .setMaxAutomaticTokenAssociations(Int32Value.of(10))
                .setMemo(StringValue.of("CryptoUpdateAccount memo"))
                .setProxyAccountID(PROXY_UPDATE)
                .setReceiverSigRequiredWrapper(BoolValue.of(false)));
    }

    private Transaction cryptoTransferTransaction() {
        return buildTransaction(builder -> {
            for (int i = 0; i < additionalTransfers.length; i++) {
                builder.getCryptoTransferBuilder().getTransfersBuilder()
                        .addAccountAmounts(accountAmount(additionalTransfers[i], additionalTransferAmounts[i]));
            }
        });
    }

    private void groupCryptoTransfersByAccountId(final TransactionRecord.Builder recordBuilder,
                                                 final List<AccountAmount.Builder> amountsToBeAdded) {
        final var accountAmounts = recordBuilder.getTransferListBuilder().getAccountAmountsBuilderList();

        var transfers = new HashMap<AccountID, Long>();
        Stream.concat(accountAmounts.stream(), amountsToBeAdded.stream())
                .forEach(accountAmount ->
                        transfers.compute(accountAmount.getAccountID(), (k, v) -> {
                            long currentValue = (v == null) ? 0 : v;
                            return currentValue + accountAmount.getAmount();
                        })
                );

        TransferList.Builder transferListBuilder = TransferList.newBuilder();
        transfers.entrySet().forEach(entry -> {
            AccountAmount accountAmount = AccountAmount.newBuilder().setAccountID(entry.getKey())
                    .setAmount(entry.getValue()).build();
            transferListBuilder.addAccountAmounts(accountAmount);
        });
        recordBuilder.setTransferList(transferListBuilder);
    }

    private void testRawBytes(Transaction transaction, byte[] expectedBytes) {
        // given
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        // when
        parseRecordItemAndCommit(new RecordItem(transaction, record));

        // then
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertArrayEquals(expectedBytes, dbTransaction.getTransactionBytes());
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum responseCode) {
        return transactionRecord(transactionBody, responseCode.getNumber(), recordBuilder -> {
        });
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int responseCode) {
        return transactionRecord(transactionBody, responseCode, recordBuilder -> {
        });
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int status,
                                                Consumer<TransactionRecord.Builder> builderConsumer) {
        return buildTransactionRecord(recordBuilder -> {
                    recordBuilder.getReceiptBuilder().setAccountID(accountId1);
                    builderConsumer.accept(recordBuilder);
                },
                transactionBody,
                status);
    }

    private TransactionRecord transactionRecordSuccess(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecordSuccess(TransactionBody transactionBody,
                                                       Consumer<TransactionRecord.Builder> customBuilder) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS.getNumber(), customBuilder);
    }
}
