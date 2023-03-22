package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.STAKING_REWARD_ACCOUNT;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Condition;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.CryptoAllowanceRepository;
import com.hedera.mirror.importer.repository.NftAllowanceRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.mirror.importer.util.UtilityTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerCryptoTest extends AbstractEntityRecordItemListenerTest {
    private static final long INITIAL_BALANCE = 1000L;
    private static final AccountID accountId1 = AccountID.newBuilder().setAccountNum(1001).build();
    private static final long[] additionalTransfers = {5000};
    private static final long[] additionalTransferAmounts = {1001, 1002};
    private static final ByteString ALIAS_KEY = DomainUtils.fromBytes(UtilityTest.ALIAS_ECDSA_SECP256K1);

    private final ContractRepository contractRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final RecordParserProperties parserProperties;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setClaims(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setTransactionBytes(false);
    }

    @Test
    void cryptoApproveAllowance() {
        // given
        var consensusTimestamp = recordItemBuilder.timestamp();
        var expectedNfts = new ArrayList<Nft>();
        var nftAllowances = customizeNftAllowances(consensusTimestamp, expectedNfts);
        RecordItem recordItem = recordItemBuilder.cryptoApproveAllowance()
                .transactionBody(b -> b.clearNftAllowances().addAllNftAllowances(nftAllowances))
                .record(r -> r.setConsensusTimestamp(consensusTimestamp))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertAllowances(recordItem, expectedNfts);
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        var accountEntityId = EntityId.of(accountId1);
        var consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(new CryptoTransfer.Id(
                initialBalance, consensusTimestamp, accountEntityId.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountEntityId),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, initialBalance, record.getConsensusTimestamp()),
                () -> assertEquals(initialBalance, dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isPresent()
        );
    }

    @Test
    void cryptoCreateWithZeroInitialBalance() {
        CryptoCreateTransactionBody.Builder cryptoCreateBuilder = cryptoCreateAccountBuilderWithDefaults()
                .setInitialBalance(0L);
        Transaction transaction = cryptoCreateTransaction(cryptoCreateBuilder);
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        var accountEntityId = EntityId.of(accountId1);
        var consensusTimestamp = DomainUtils.timeStampInNanos(record.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(
                new CryptoTransfer.Id(0L, consensusTimestamp, accountEntityId.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountEntityId),
                () -> assertCryptoTransfers(3),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, 0L, record.getConsensusTimestamp()),
                () -> assertThat(dbTransaction.getInitialBalance()).isZero(),
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(accountId1)),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, record),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, initialBalance, record.getConsensusTimestamp()),
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
                () -> assertCryptoEntity(cryptoCreateTransactionBody, 0L, record.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isEmpty(),
                () -> assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray())).get()
                        .isEqualTo(accountEntityId.getId())
        );
    }

    @Test
    void cryptoDeleteAllowance() {
        // given
        var delegatingSpender = EntityId.of(recordItemBuilder.accountId());
        var ownerAccountId = EntityId.of(recordItemBuilder.accountId());
        var spender1 = EntityId.of(recordItemBuilder.accountId());
        var spender2 = EntityId.of(recordItemBuilder.accountId());
        var tokenId1 = EntityId.of(recordItemBuilder.tokenId());
        var tokenId2 = EntityId.of(recordItemBuilder.tokenId());
        List<NftRemoveAllowance> nftRemoveAllowances = List.of(
                NftRemoveAllowance.newBuilder()
                        .setOwner(AccountID.newBuilder().setAccountNum(ownerAccountId.getEntityNum()))
                        .setTokenId(TokenID.newBuilder().setTokenNum(tokenId1.getEntityNum()))
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .build(),
                NftRemoveAllowance.newBuilder()
                        .setOwner(AccountID.newBuilder().setAccountNum(ownerAccountId.getEntityNum()))
                        .setTokenId(TokenID.newBuilder().setTokenNum(tokenId2.getEntityNum()))
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .addSerialNumbers(2L)
                        .build()
        );
        RecordItem recordItem = recordItemBuilder.cryptoDeleteAllowance()
                .transactionBody(b -> b.clearNftAllowances().addAllNftAllowances(nftRemoveAllowances))
                .build();

        var nft1 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(10L)
                .deleted(false)
                .id(new NftId(1L, tokenId1))
                .modifiedTimestamp(recordItem.getConsensusTimestamp())
                .build();
        var nft2 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(11L)
                .deleted(false)
                .id(new NftId(2L, tokenId1))
                .modifiedTimestamp(recordItem.getConsensusTimestamp())
                .build();
        var nft3 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(12L)
                .deleted(false)
                .id(new NftId(1L, tokenId2))
                .modifiedTimestamp(recordItem.getConsensusTimestamp())
                .build();
        var nft4 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(13L)
                .deleted(false)
                .id(new NftId(2L, tokenId2))
                .modifiedTimestamp(recordItem.getConsensusTimestamp())
                .build();
        List<Nft> nftsWithAllowance = Stream.of(
                nft1.toBuilder().delegatingSpender(delegatingSpender).modifiedTimestamp(15L).spender(spender1),
                nft2.toBuilder().modifiedTimestamp(16L).spender(spender2),
                nft3.toBuilder().modifiedTimestamp(17L).spender(spender1),
                nft4.toBuilder().modifiedTimestamp(18L).spender(spender2)
        ).map(Nft.NftBuilder::build).collect(Collectors.toList());
        nftRepository.saveAll(nftsWithAllowance);

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertAll(
                () -> assertEquals(0, entityRepository.count()),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()),
                () -> assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3, nft4)
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());
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
                () -> assertFalse(dbAccountEntity.getReceiverSigRequired()),
                () -> assertFalse(dbAccountEntity.getDeclineReward()),
                () -> assertEquals(cryptoUpdateTransactionBody.getStakedNodeId(), dbAccountEntity.getStakedNodeId()),
                () -> assertEquals(AbstractEntity.ACCOUNT_ID_CLEARED, dbAccountEntity.getStakedAccountId()),
                () -> assertEquals(Utility.getEpochDay(DomainUtils.timestampInNanosMax(record.getConsensusTimestamp())),
                        dbAccountEntity.getStakePeriodStart())
        );
    }

    @Test
    void cryptoTransferWithPaidStakingRewards() {
        // given
        var receiver1 = domainBuilder.entity().customize(e -> e.balance(100L)).persist();
        var receiver2 = domainBuilder.entity().customize(e -> e.balance(200L)).persist();
        var sender = domainBuilder.entity().customize(e -> e.balance(300L)).persist();

        var recordItem = recordItemBuilder.cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(accountAmount(sender.getId(), -20L))
                        .addAccountAmounts(accountAmount(receiver1.getId(), 5L))
                        .addAccountAmounts(accountAmount(receiver2.getId(), -15L))))
                .record(r -> {
                    // preserve the tx fee paid by the payer and received by the node and the fee collector
                    // sender only gets deducted 15, since it gets a 5 reward payout
                    // receiver1 gets 9, since it gets a 4 reward payout
                    // receiver2 gets no reward
                    var paidStakingRewards = List.of(
                            accountAmount(sender.getId(), 5L).build(),
                            accountAmount(receiver1.getId(), 4L).build()
                    );

                    var transferList = r.getTransferList().toBuilder()
                            .addAccountAmounts(accountAmount(sender.getId(), -15L))
                            .addAccountAmounts(accountAmount(receiver1.getId(), 9L))
                            .addAccountAmounts(accountAmount(receiver2.getId(), 15L))
                            .addAccountAmounts(accountAmount(STAKING_REWARD_ACCOUNT, -9L));
                    r.setTransferList(transferList).addAllPaidStakingRewards(paidStakingRewards);
                })
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long expectedStakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        sender.setBalance(285L);
        sender.setStakePeriodStart(expectedStakePeriodStart);
        receiver1.setBalance(109L);
        receiver1.setStakePeriodStart(expectedStakePeriodStart);
        receiver2.setBalance(215L);

        var payerAccountId = recordItem.getPayerAccountId();
        var expectedStakingRewardTransfer1 = new StakingRewardTransfer();
        expectedStakingRewardTransfer1.setAccountId(sender.getId());
        expectedStakingRewardTransfer1.setAmount(5L);
        expectedStakingRewardTransfer1.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer1.setPayerAccountId(payerAccountId);
        var expectedStakingRewardTransfer2 = new StakingRewardTransfer();
        expectedStakingRewardTransfer2.setAccountId(receiver1.getId());
        expectedStakingRewardTransfer2.setAmount(4L);
        expectedStakingRewardTransfer2.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer2.setPayerAccountId(payerAccountId);

        assertAll(
                () -> assertEquals(0, contractRepository.count()),
                // 3 for fee, 3 for hbar transfers, and 1 for reward payout from 0.0.800
                () -> assertEquals(7, cryptoTransferRepository.count()),
                () -> assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(sender, receiver1, receiver2),
                () -> assertThat(stakingRewardTransferRepository.findAll()).containsExactlyInAnyOrder(
                        expectedStakingRewardTransfer1, expectedStakingRewardTransfer2
                ),
                () -> assertEquals(1, transactionRepository.count())
        );
    }

    @Test
    void cryptoTransferFailedWithPaidStakingRewards() {
        // given
        var payer = domainBuilder.entity().customize(e -> e.balance(5000L)).persist();
        var receiver = domainBuilder.entity().customize(e -> e.balance(0L)).persist();
        var sender = domainBuilder.entity().customize(e -> e.balance(5L)).persist();

        // Transaction failed with INSUFFICIENT_ACCOUNT_BALANCE because sender's balance is less than the intended
        // transfer amount. However, the transaction payer has a balance change and there is pending reward for the
        // payer account, so there will be a reward payout for the transaction payer.
        var transactionId = transactionId(payer, domainBuilder.timestamp());
        var recordItem = recordItemBuilder.cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(accountAmount(sender.getId(), -20L))
                        .addAccountAmounts(accountAmount(receiver.getId(), 20L))))
                .transactionBodyWrapper(b -> b.setTransactionID(transactionId))
                .record(r -> r.setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(payer.getId(), -2800L))
                                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 1000L))
                                .addAccountAmounts(accountAmount(TREASURY.getAccountNum(), 2000L))
                                .addAccountAmounts(accountAmount(STAKING_REWARD_ACCOUNT, -200L)))
                        .addPaidStakingRewards(accountAmount(payer.getId(), 200L))
                        .getReceiptBuilder().setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long expectedStakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        payer.setBalance(2200L);
        payer.setStakePeriodStart(expectedStakePeriodStart);

        var expectedStakingRewardTransfer = new StakingRewardTransfer();
        expectedStakingRewardTransfer.setAccountId(payer.getId());
        expectedStakingRewardTransfer.setAmount(200L);
        expectedStakingRewardTransfer.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer.setPayerAccountId(payer.toEntityId());

        assertAll(
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(payer, sender, receiver),
                () -> assertThat(stakingRewardTransferRepository.findAll()).containsOnly(expectedStakingRewardTransfer),
                () -> assertEquals(1, transactionRepository.count())
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
        var updateTransaction = buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountId1)
                // *** THIS IS THE OVERFLOW WE WANT TO TEST ***
                // This should result in the entity having a Long.MAX_VALUE or Long.MIN_VALUE expirations
                // (the results of overflows).
                .setExpirationTime(Timestamp.newBuilder().setSeconds(seconds))
                .setDeclineReward(BoolValue.of(true))
                .setStakedAccountId(AccountID.newBuilder().setAccountNum(1L).build()));
        var transactionBody = getTransactionBody(updateTransaction);

        var record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(updateTransaction).build());

        var dbAccountEntity = getTransactionEntity(record.getConsensusTimestamp());
        var stakedAccountId = EntityId.of(transactionBody.getCryptoUpdateAccount().getStakedAccountId()).getId();

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpirationTimestamp()),
                () -> assertTrue(dbAccountEntity.getDeclineReward()),
                () -> assertEquals(stakedAccountId, dbAccountEntity.getStakedAccountId()),
                () -> assertEquals(-1L, dbAccountEntity.getStakedNodeId())
        );
    }

    @Test
    void cryptoUpdateFailedTransaction() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionRecord createRecord = transactionRecordSuccess(getTransactionBody(createTransaction));
        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(createRecord).transaction(createTransaction).build());

        // now update
        Transaction transaction = cryptoUpdateTransaction(accountId1);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
    void cryptoUpdateSuccessfulTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder.entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        long newStakedNodeId = 5L;
        var protoAccountId = AccountID.newBuilder().setAccountNum(account.getNum()).build();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder.cryptoUpdate()
                .transactionBody(b -> b.setStakedNodeId(newStakedNodeId).setAccountIDToUpdate(protoAccountId))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(STAKING_REWARD_ACCOUNT, -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 5L))
                                .addAccountAmounts(accountAmount(TREASURY.getAccountNum(), 15L))))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        long expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(newStakedNodeId, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart)
        );
    }

    @Test
    void cryptoUpdateMemoSuccessfulTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder.entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        var protoAccountId = AccountID.newBuilder().setAccountNum(account.getNum()).build();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder.cryptoUpdate()
                .transactionBody(b -> b.clearDeclineReward()
                        .clearStakedAccountId()
                        .clearStakedNodeId()
                        .setAccountIDToUpdate(protoAccountId)
                        .setMemo(StringValue.of("new memo")))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(STAKING_REWARD_ACCOUNT, -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 5L))
                                .addAccountAmounts(accountAmount(TREASURY.getAccountNum(), 15L))))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        var expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1;
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(1L, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart)
        );
    }

    @Test
    void cryptoUpdateFailedTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder.entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        var protoAccountId = AccountID.newBuilder().setAccountNum(account.getNum()).build();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder.cryptoUpdate()
                .transactionBody(b -> b.setStakedNodeId(5L).setAccountIDToUpdate(protoAccountId))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(STAKING_REWARD_ACCOUNT, -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 5L))
                                .addAccountAmounts(accountAmount(TREASURY.getAccountNum(), 15L))))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        long expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1;
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(1L, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart)
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
                        .usingOverriddenEquals()
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
        var recordLiveHash = transactionRecordSuccess(getTransactionBody(transactionAddLiveHash));
        var recordItem = RecordItem.builder().transactionRecord(recordLiveHash).transaction(transactionAddLiveHash).build();
        parseRecordItemAndCommit(recordItem);

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoDeleteLiveHashTransactionBody deleteLiveHashTransactionBody = transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord record = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
        var tokenId = EntityId.of(1020L, EntityType.TOKEN);
        long amount = 100L;

        TransactionRecord record = buildTransactionRecord(r -> {
            r.setConsensusTimestamp(TestUtils.toTimestamp(1577836799000000000L - 1));
            for (int i = 0; i < additionalTransfers.length; i++) {
                // Add non-fee transfers to record
                var accountAmount = accountAmount(additionalTransfers[i], additionalTransferAmounts[i]);
                r.getTransferListBuilder().addAccountAmounts(accountAmount);
            }
            r.addTokenTransferLists(TokenTransferList.newBuilder()
                    .setToken(TokenID.newBuilder().setTokenNum(tokenId.getEntityNum()))
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(accountId1)
                            .setAmount(amount)));
        }, transactionBody, ResponseCodeEnum.FAIL_INVALID.getNumber());

        var recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count(), "Node, network fee & errata"),
                () -> assertEquals(0, nonFeeTransferRepository.count()),
                () -> assertThat(tokenTransferRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(tokenId, t -> t.getId().getTokenId())
                        .returns(amount, t -> t.getAmount())
                        .returns(EntityId.of(accountId1), t -> t.getId().getAccountId()),
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
    void cryptoTransferHasCorrectIsApprovalValue() {
        final long[] accountNums = {PAYER.getAccountNum(), PAYER2.getAccountNum(), PAYER3.getAccountNum()};
        final long[] amounts = {210, -300, 15};
        final boolean[] isApprovals = {false, true, false};
        Transaction transaction = buildTransaction(r -> {
            for (int i = 0; i < accountNums.length; i++) {
                var accountAmount = accountAmount(accountNums[i], amounts[i]).setIsApproval(isApprovals[i]).build();
                r.getCryptoTransferBuilder()
                        .getTransfersBuilder()
                        .addAccountAmounts(accountAmount);
            }
        });
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = buildTransactionRecordWithNoTransactions(builder -> {
            for (int i = 0; i < accountNums.length; i++) {
                var accountAmount = accountAmount(accountNums[i], amounts[i]).setIsApproval(false).build();
                builder.getTransferListBuilder()
                        .addAccountAmounts(accountAmount);
            }
        }, transactionBody, ResponseCodeEnum.SUCCESS.getNumber());

        var recordItem = RecordItem.builder().transactionRecord(record).transaction(transaction).build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(amounts.length, cryptoTransferRepository.count()),
                () -> {
                    for (var cryptoTransfer : cryptoTransferRepository.findAll()) {
                        for (int i = 0; i < isApprovals.length; i++) {
                            if (cryptoTransfer.getEntityId() != accountNums[i]) {
                                continue;
                            }
                            assertThat(cryptoTransfer)
                                    .extracting(CryptoTransfer::getIsApproval)
                                    .isEqualTo(isApprovals[i]);
                        }
                    }
                }
        );
    }

    @Test
    void cryptoTransferWithAlias() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setNonFeeTransfers(true);
        Entity entity = domainBuilder.entity().persist();
        var newAccount = AccountID.newBuilder().setAccountNum(domainBuilder.id()).build();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray())).isNotPresent();

        // Crypto create alias account
        Transaction accountCreateTransaction = cryptoCreateTransaction();
        TransactionBody accountCreateTransactionBody = getTransactionBody(accountCreateTransaction);
        TransactionRecord recordCreate = buildTransactionRecord(
                recordBuilder -> recordBuilder.setAlias(ALIAS_KEY).getReceiptBuilder().setAccountID(newAccount),
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

        parseRecordItemsAndCommit(List.of(
                RecordItem.builder().transactionRecord(recordCreate).transaction(accountCreateTransaction).build(),
                RecordItem.builder().transactionRecord(recordTransfer).transaction(transaction).build()
        ));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(newAccount), entity.toEntityId()),
                () -> assertCryptoTransfers(6)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2)),
                () -> assertEquals(additionalTransfers.length * 2 + 2, nonFeeTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, recordTransfer),
                () -> assertThat(findNonFeeTransfers())
                        .extracting(NonFeeTransfer::getEntityId)
                        .extracting(EntityId::getEntityNum)
                        .contains(newAccount.getAccountNum(), entity.getNum())
        );
    }

    @Test
    void cryptoTransferWithEvmAddressAlias() {
        Entity contract = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddress(contract.getEvmAddress())).isPresent();

        entityProperties.getPersist().setNonFeeTransfers(true);

        long transferAmount = 123;
        var transfer1 = accountAliasAmount(DomainUtils.fromBytes(contract.getEvmAddress()), transferAmount).build();
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder().getTransfersBuilder()
                .addAccountAmounts(transfer1));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = transactionRecordSuccess(transactionBody);
        RecordItem recordItem = RecordItem.builder().transactionRecord(transactionRecord).transaction(transaction).build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, nonFeeTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, transactionRecord),
                () -> assertThat(findNonFeeTransfers())
                        .allSatisfy(nonFeeTransfer -> {
                            assertThat(nonFeeTransfer.getEntityId()).isEqualTo(contract.toEntityId());
                            assertThat(nonFeeTransfer.getAmount()).isEqualTo(transferAmount);
                            assertThat(nonFeeTransfer.getPayerAccountId()).isEqualTo(recordItem.getPayerAccountId());
                        })
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
        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(transactionRecord).transaction(transaction).build());

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

        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();

        // when, then
        assertThrows(AliasNotFoundException.class,
                () -> parseRecordItemAndCommit(recordItem));
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

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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

    @Test
    void finalizeHollowAccountToContract() {
        // given
        var accountId = recordItemBuilder.accountId();
        var contractId = ContractID.newBuilder().setContractNum(accountId.getAccountNum()).build();
        var evmAddress = recordItemBuilder.evmAddress();
        var cryptoCreate = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.clearAlias().clearKey().setAlias(evmAddress.getValue()))
                .receipt(r -> r.setAccountID(accountId))
                .build();
        var contractCreate = recordItemBuilder.contractCreate()
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs().setContractID(contractId).setEvmAddress(evmAddress))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(cryptoCreate, contractCreate));

        // then
        long createdTimestamp = cryptoCreate.getConsensusTimestamp();
        var expectedAccount = Entity.builder()
                .createdTimestamp(createdTimestamp)
                .evmAddress(DomainUtils.toBytes(evmAddress.getValue()))
                .id(accountId.getAccountNum())
                .timestampRange(Range.closedOpen(createdTimestamp, contractCreate.getConsensusTimestamp()))
                .type(EntityType.ACCOUNT)
                .build();
        var expectedContract = expectedAccount.toBuilder()
                .timestampRange(Range.atLeast(contractCreate.getConsensusTimestamp()))
                .type(EntityType.CONTRACT)
                .build();
        var expectedFileId = EntityId.of(contractCreate.getTransactionBody().getContractCreateInstance().getFileID());
        String[] fields = new String[]{"createdTimestamp", "evmAddress", "id", "timestampRange", "type"};
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedContract);
        assertThat(findHistory(Entity.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount);
        assertThat(contractRepository.findById(expectedContract.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId);
    }

    @Test
    void finalizeHollowAccountToContractInTwoRecordFiles() {
        // given
        var accountId = recordItemBuilder.accountId();
        var evmAddress = recordItemBuilder.evmAddress();
        var cryptoCreate = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.clearAlias().clearKey().setAlias(evmAddress.getValue()))
                .receipt(r -> r.setAccountID(accountId))
                .build();

        // when
        parseRecordItemAndCommit(cryptoCreate);

        // then
        var expectedAccount = Entity.builder()
                .createdTimestamp(cryptoCreate.getConsensusTimestamp())
                .evmAddress(DomainUtils.toBytes(evmAddress.getValue()))
                .id(accountId.getAccountNum())
                .timestampRange(Range.atLeast(cryptoCreate.getConsensusTimestamp()))
                .type(EntityType.ACCOUNT)
                .build();
        String[] fields = new String[]{"createdTimestamp", "evmAddress", "id", "timestampRange", "type"};
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount);
        assertThat(findHistory(Entity.class)).isEmpty();

        // when
        var contractId = ContractID.newBuilder().setContractNum(accountId.getAccountNum()).build();
        var contractCreate = recordItemBuilder.contractCreate()
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs().setContractID(contractId).setEvmAddress(evmAddress))
                .build();
        parseRecordItemAndCommit(contractCreate);

        // then
        expectedAccount.setTimestampUpper(contractCreate.getConsensusTimestamp());
        var expectedContract = expectedAccount.toBuilder()
                .timestampRange(Range.atLeast(contractCreate.getConsensusTimestamp()))
                .type(EntityType.CONTRACT)
                .build();
        var expectedFileId = EntityId.of(contractCreate.getTransactionBody().getContractCreateInstance().getFileID());
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedContract);
        assertThat(findHistory(Entity.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount);
        assertThat(contractRepository.findById(expectedContract.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId);
    }

    private void assertAllowances(RecordItem recordItem, Collection<Nft> expectedNfts) {
        assertAll(
                () -> assertEquals(1, cryptoAllowanceRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(3, nftAllowanceRepository.count()),
                () -> assertEquals(1, tokenAllowanceRepository.count()),
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()),
                () -> assertThat(cryptoAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getAmount()).isPositive())
                        .allSatisfy(a -> assertThat(a.getOwner()).isPositive())
                        .allSatisfy(a -> assertThat(a.getSpender()).isPositive())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(nftAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getOwner()).isPositive())
                        .allSatisfy(a -> assertThat(a.getSpender()).isPositive())
                        .allSatisfy(a -> assertThat(a.getTokenId()).isPositive())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNfts),
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

    private void assertCryptoEntity(CryptoCreateTransactionBody expected, long expectedBalance,
                                    Timestamp consensusTimestamp) {
        Entity actualAccount = getTransactionEntity(consensusTimestamp);
        long timestamp = DomainUtils.timestampInNanosMax(consensusTimestamp);
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualAccount.getAutoRenewPeriod()),
                () -> assertEquals(expectedBalance, actualAccount.getBalance()),
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

    private List<NftAllowance> customizeNftAllowances(Timestamp consensusTimestamp, List<Nft> expectedNfts) {
        var delegatingSpender = recordItemBuilder.accountId();
        var owner = recordItemBuilder.accountId();
        var spender1 = recordItemBuilder.accountId();
        var spender2 = recordItemBuilder.accountId();
        var tokenId = recordItemBuilder.tokenId();
        var nft1 = Nft.builder()
                .id(new NftId(1L, EntityId.of(tokenId)))
                .accountId(EntityId.of(owner))
                .createdTimestamp(101L)
                .deleted(false)
                .modifiedTimestamp(101L)
                .build();
        var nft2 = Nft.builder()
                .id(new NftId(2L, EntityId.of(tokenId)))
                .accountId(EntityId.of(owner))
                .createdTimestamp(102L)
                .deleted(false)
                .modifiedTimestamp(102L)
                .build();
        var nft3 = Nft.builder()
                .id(new NftId(3L, EntityId.of(tokenId)))
                .accountId(EntityId.of(owner))
                .createdTimestamp(103L)
                .deleted(false)
                .modifiedTimestamp(103L)
                .build();
        var timestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        List<NftAllowance> nftAllowances = new ArrayList<>();

        nftAllowances.add(NftAllowance.newBuilder()
                .setDelegatingSpender(delegatingSpender)
                .setOwner(owner)
                .addSerialNumbers(1L)
                .addSerialNumbers(2L)
                .setSpender(spender1)
                .setTokenId(tokenId)
                .build());
        expectedNfts.add(nft1.toBuilder()
                .delegatingSpender(EntityId.of(delegatingSpender))
                .modifiedTimestamp(timestamp)
                .spender(EntityId.of(spender1))
                .build());

        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(false))
                .setOwner(recordItemBuilder.accountId())
                .setSpender(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId())
                .build());
        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(true))
                .setOwner(recordItemBuilder.accountId())
                .setSpender(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId())
                .build());

        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(true))
                .setOwner(owner)
                .addSerialNumbers(2L)
                .addSerialNumbers(3L)
                .setSpender(spender2)
                .setTokenId(tokenId)
                .build());

        // duplicate nft allowance
        nftAllowances.add(nftAllowances.get(nftAllowances.size() - 1));

        // serial number 2's allowance is granted twice, the allowance should be granted to spender2 since it appears
        // after the nft allowance to spender1
        expectedNfts.add(nft2.toBuilder().modifiedTimestamp(timestamp).spender(EntityId.of(spender2)).build());
        expectedNfts.add(nft3.toBuilder().modifiedTimestamp(timestamp).spender(EntityId.of(spender2)).build());

        nftRepository.saveAll(List.of(nft1, nft2, nft3));

        return nftAllowances;
    }

    private void createAccount() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);
        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(createRecord).transaction(createTransaction).build());
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
        return cryptoUpdateTransaction(accountNum, b -> {
        });
    }

    private Transaction cryptoUpdateTransaction(AccountID accountNum,
                                                Consumer<CryptoUpdateTransactionBody.Builder> custom) {
        return buildTransaction(builder -> {
            var cryptoBuilder = builder.getCryptoUpdateAccountBuilder()
                    .setAccountIDToUpdate(accountNum)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                    .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                    .setKey(keyFromString(KEY))
                    .setMaxAutomaticTokenAssociations(Int32Value.of(10))
                    .setMemo(StringValue.of("CryptoUpdateAccount memo"))
                    .setProxyAccountID(PROXY_UPDATE)
                    .setReceiverSigRequiredWrapper(BoolValue.of(false))
                    .setStakedNodeId(1L);
            custom.accept(cryptoBuilder);
        });
    }

    private Transaction cryptoTransferTransaction() {
        return buildTransaction(builder -> {
            for (int i = 0; i < additionalTransfers.length; i++) {
                builder.getCryptoTransferBuilder().getTransfersBuilder()
                        .addAccountAmounts(accountAmount(additionalTransfers[i], additionalTransferAmounts[i]));
            }
        });
    }

    private void groupCryptoTransfersByAccountId(TransactionRecord.Builder recordBuilder,
                                                 List<AccountAmount.Builder> amountsToBeAdded) {
        var accountAmounts = recordBuilder.getTransferListBuilder().getAccountAmountsBuilderList();

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
        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

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
