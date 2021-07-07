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
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.config.CacheConfiguration;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.CustomFeeWrapper;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

public class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {

    private static final long ASSOCIATE_TIMESTAMP = 5L;
    private static final long AUTO_RENEW_PERIOD = 30L;
    private static final long CREATE_TIMESTAMP = 1L;
    private static final Timestamp EXPIRY_TIMESTAMP = Timestamp.newBuilder().setSeconds(360L).build();
    private static final long EXPIRY_NS = EXPIRY_TIMESTAMP.getSeconds() * 1_000_000_000 + EXPIRY_TIMESTAMP.getNanos();
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_1 = EntityIdEndec.decode(1199, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_2 = EntityIdEndec.decode(1200, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_DOMAIN_TOKEN_ID = EntityIdEndec.decode(9800, EntityTypeEnum.TOKEN);
    private static final long INITIAL_SUPPLY = 1_000_000L;
    private static final String METADATA = "METADATA";
    private static final long SERIAL_NUMBER_1 = 1L;
    private static final long SERIAL_NUMBER_2 = 2L;
    private static final List<Long> SERIAL_NUMBER_LIST = Arrays.asList(SERIAL_NUMBER_1, SERIAL_NUMBER_2);
    private static final String SYMBOL = "FOOCOIN";
    private static final String TOKEN_CREATE_MEMO = "TokenCreate memo";
    private static final TokenID TOKEN_ID = TokenID.newBuilder().setTokenNum(2).build();
    private static final EntityId DOMAIN_TOKEN_ID = EntityId.of(TOKEN_ID);
    private static final Key TOKEN_REF_KEY = keyFromString(KEY);
    private static final long TOKEN_UPDATE_AUTO_RENEW_PERIOD = 12L;
    private static final Key TOKEN_UPDATE_REF_KEY = keyFromString(KEY2);
    private static final String TOKEN_UPDATE_MEMO = "TokenUpdate memo";
    private static final long TRANSFER_TIMESTAMP = 15L;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    protected TokenRepository tokenRepository;

    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    @Resource
    protected TokenTransferRepository tokenTransferRepository;

    @Resource
    protected NftRepository nftRepository;

    @Resource
    protected NftTransferRepository nftTransferRepository;

    @Qualifier(CacheConfiguration.EXPIRE_AFTER_30M)
    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setTokens(true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideCustomFees")
    void tokenCreate(String name, List<CustomFee> customFees) {
        // given
        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);

        // when
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false, customFees);

        // then
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        // verify token
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertCustomFeesInDb(customFees);
        assertThat(tokenTransferRepository.count()).isEqualTo(1L);
    }

    @Test
    void tokenCreateWithoutPersistence() {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isZero();
        assertCustomFeesInDb(Lists.emptyList());
    }

    @Test
    void tokenCreateWithNfts() {
        createTokenEntity(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, false, false);

        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        // verify token
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, 0);
        assertCustomFeesInDb(deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID));
    }

    @Test
    void tokenAssociate() {
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenAssociateWithMissingToken() {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        // verify token account was not created
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenDissociate() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTransaction, dissociateTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, dissociateTimeStamp, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenDelete() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        long deleteTimeStamp = 10L;
        insertAndParseTransaction(deleteTransaction, deleteTimeStamp, INITIAL_SUPPLY);

        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                true, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, deleteTimeStamp);
        assertEquals(4, entityRepository.count()); // Node, payer, token and autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenFeeScheduleUpdate() {
        // given
        // create the token entity with empty custom fees
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false);
        // update fee schedule
        long updateTimestamp = CREATE_TIMESTAMP + 10L;
        Entity expectedEntity = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);
        List<CustomFee> newCustomFees = nonEmptyCustomFees(updateTimestamp, DOMAIN_TOKEN_ID);
        List<CustomFee> expectedCustomFees = Lists.newArrayList(deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID));
        expectedCustomFees.addAll(newCustomFees);

        // when
        updateTokenFeeSchedule(TOKEN_ID, updateTimestamp, newCustomFees);

        // then
        assertEntity(expectedEntity);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertCustomFeesInDb(expectedCustomFees);
    }

    @Test
    void tokenUpdate() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                TOKEN_UPDATE_REF_KEY,
                PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(transaction, updateTimeStamp, INITIAL_SUPPLY);

        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_UPDATE_REF_KEY, EntityId.of(PAYER2),
                TOKEN_UPDATE_AUTO_RENEW_PERIOD, false, EXPIRY_NS, TOKEN_UPDATE_MEMO, null, CREATE_TIMESTAMP,
                updateTimeStamp);
        assertEquals(5, entityRepository.count()); // Node, payer, token, old autorenew, and new autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, updateTimeStamp, newSymbol, INITIAL_SUPPLY,
                TOKEN_UPDATE_REF_KEY.toByteArray(), "feeScheduleKey", "freezeKey", "kycKey", "supplyKey",
                "wipeKey");
    }

    @Test
    void tokenUpdateWithMissingToken() {
        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                keyFromString("updated-key"),
                AccountID.newBuilder().setAccountNum(2002).build());
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenAccountFreeze() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, true, false, INITIAL_SUPPLY);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(transaction, freezeTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, freezeTimeStamp, true,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountUnfreeze() {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTransaction, freezeTimeStamp, INITIAL_SUPPLY);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTransaction, unfreezeTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, unfreezeTimeStamp, true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountGrantKyc() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, true, INITIAL_SUPPLY);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() {
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(transaction, grantTimeStamp, INITIAL_SUPPLY);

        // verify token account was not created when missing
        assertTokenAccountInRepository(TOKEN_ID, PAYER, false, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountRevokeKyc() {
        // create token with kyc revoked
        createTokenEntity(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(associateTransaction, ASSOCIATE_TIMESTAMP, INITIAL_SUPPLY);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTransaction, grantTimeStamp, INITIAL_SUPPLY);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimestamp = 333;
        insertAndParseTransaction(revokeTransaction, revokeTimestamp, INITIAL_SUPPLY);

        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, revokeTimestamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenBurn() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, false, amount, null);
        insertAndParseTransaction(transaction, burnTimestamp, INITIAL_SUPPLY - amount, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, burnTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, INITIAL_SUPPLY - amount);
    }

    @Test
    void tokenBurnNft() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTransaction, mintTimestamp, SERIAL_NUMBER_LIST
                .size(), SERIAL_NUMBER_LIST, mintTransfer);

        long burnTimestamp = 15L;
        TokenTransferList burnTranfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, Arrays
                .asList(SERIAL_NUMBER_1));
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, false, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTransaction, burnTimestamp, 0, burnTranfer);

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(3L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(burnTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, PAYER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, burnTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenBurnNftMissingNft() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, Arrays
                .asList(SERIAL_NUMBER_2));
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                Arrays.asList(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTransaction, mintTimestamp, SERIAL_NUMBER_LIST.size(),
                Arrays.asList(SERIAL_NUMBER_2), mintTransfer);

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER,
                Arrays.asList(SERIAL_NUMBER_1));
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, false, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTransaction, burnTimestamp, 0, burnTransfer);

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(burnTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, PAYER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, mintTimestamp, burnTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), true);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @Test
    void tokenMint() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, true, amount, null);
        insertAndParseTransaction(transaction, mintTimestamp, INITIAL_SUPPLY + amount, tokenTransfer);

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);
    }

    @Test
    void tokenMintNfts() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP, PAYER, false, false, 0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        // Verify
        insertAndParseTransaction(transaction, mintTimestamp, 2, SERIAL_NUMBER_LIST, mintTransfer);
        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 2);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @Test
    void tokenMintNftsMissingToken() {
        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 2,
                SERIAL_NUMBER_LIST);

        // Verify
        insertAndParseTransaction(transaction, mintTimestamp, 1, SERIAL_NUMBER_LIST, mintTransfer);
        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransfer(String name, List<AssessedCustomFee> assessedCustomFees) {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);
        TokenID tokenID2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 10L, false, false);

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-1000).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(1000).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(333).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(-333).build())
                .build();

        insertAndParseTransactionWithCustomFees(transaction, TRANSFER_TIMESTAMP, INITIAL_SUPPLY, assessedCustomFees,
                transferList1, transferList2);

        assertTokenTransferInRepository(TOKEN_ID, PAYER, TRANSFER_TIMESTAMP, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenTransferInRepository(tokenID2, PAYER, TRANSFER_TIMESTAMP, 333);
        assertTokenTransferInRepository(tokenID2, accountId, TRANSFER_TIMESTAMP, -333);
        assertAssessedCustomFeesInDb(assessedCustomFees);
    }

    @Test
    void nftTransfer() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        TokenID tokenID2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 15L, false, false);

        long mintTimestamp1 = 20L;
        TokenTransferList mintTransfer1 = nftTransfer(TOKEN_ID, RECEIVER, DEFAULT_ACCOUNT_ID, Arrays
                .asList(SERIAL_NUMBER_1));
        Transaction mintTransaction1 = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(SERIAL_NUMBER_1));

        // Verify
        insertAndParseTransaction(mintTransaction1, mintTimestamp1, 2, Arrays.asList(SERIAL_NUMBER_1), mintTransfer1);

        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, RECEIVER, DEFAULT_ACCOUNT_ID, Arrays
                .asList(SERIAL_NUMBER_2));
        Transaction mintTransaction2 = tokenSupplyTransaction(tokenID2, TokenType.NON_FUNGIBLE_UNIQUE, true, 0, Arrays
                .asList(SERIAL_NUMBER_2));

        // Verify
        insertAndParseTransaction(mintTransaction2, mintTimestamp2, 2, Arrays.asList(SERIAL_NUMBER_2), mintTransfer2);

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimestamp = 40L;
        insertAndParseTransaction(transaction, transferTimestamp, 0, transferList1, transferList2);

        assertThat(nftTransferRepository.count()).isEqualTo(4L);
        assertNftTransferInRepository(mintTimestamp1, SERIAL_NUMBER_1, TOKEN_ID, RECEIVER, null);
        assertNftTransferInRepository(mintTimestamp2, SERIAL_NUMBER_2, TOKEN_ID, RECEIVER, null);
        assertNftTransferInRepository(transferTimestamp, 1L, TOKEN_ID, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimestamp, 2L, tokenID2, RECEIVER, PAYER);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp1, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_2, true, mintTimestamp2, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void nftTransferMissingNft() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        TokenID tokenID2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, TokenType.FUNGIBLE_COMMON, symbol2, 15L, false, false);

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimestamp = 25L;
        insertAndParseTransaction(transaction, transferTimestamp, 0, transferList1, transferList2);

        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(transferTimestamp, 1L, TOKEN_ID, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimestamp, 2L, tokenID2, RECEIVER, PAYER);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, transferTimestamp, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_2, false, transferTimestamp, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void tokenWipe() {
        createAndAssociateToken(TOKEN_ID, TokenType.FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, INITIAL_SUPPLY);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, wipeAmount,
                Lists.emptyList());
        insertAndParseTransaction(transaction, wipeTimestamp, INITIAL_SUPPLY - wipeAmount, tokenTransfer);

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, INITIAL_SUPPLY - wipeAmount);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    @Test
    void tokenWipeNft() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTransaction, mintTimestamp, 1, SERIAL_NUMBER_LIST, mintTransfer);

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, Arrays
                .asList(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(transaction, wipeTimestamp, 0, Arrays.asList(SERIAL_NUMBER_1), wipeTransfer);

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(3L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(wipeTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, PAYER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, wipeTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenWipeWithMissingToken() {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, 100L, null);
        insertAndParseTransaction(transaction, 10L, INITIAL_SUPPLY);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenWipeNftMissingNft() {
        createAndAssociateToken(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER, false, false, 0);

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, RECEIVER, Arrays
                .asList(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, TokenType.NON_FUNGIBLE_UNIQUE, 0, Arrays
                .asList(SERIAL_NUMBER_1));
        insertAndParseTransaction(transaction, wipeTimestamp, 0, Arrays.asList(SERIAL_NUMBER_1), wipeTransfer);

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(1L);
        assertNftTransferInRepository(wipeTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, RECEIVER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, false, wipeTimestamp, wipeTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), true);
    }

    @Test
    void tokenCreateAndAssociateAndWipeInSameRecordFile() {
        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        long newTotalSupply = INITIAL_SUPPLY - wipeAmount;

        // create token with a transfer
        Transaction createTransaction = tokenCreateTransaction(TokenType.FUNGIBLE_COMMON, false, false, SYMBOL);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList createTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, INITIAL_SUPPLY);
        var createTransactionRecord = createTransactionRecord(CREATE_TIMESTAMP,
                TOKEN_ID.getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY,
                createTokenTransfer);

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        TransactionBody associateTransactionBody = getTransactionBody(associateTransaction);
        var associateRecord = createTransactionRecord(ASSOCIATE_TIMESTAMP, TOKEN_ID
                .getTokenNum(), associateTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, TokenType.FUNGIBLE_COMMON, wipeAmount, null);
        TransactionBody wipeTransactionBody = getTransactionBody(wipeTransaction);
        var wipeRecord = createTransactionRecord(wipeTimestamp,
                TOKEN_ID.getTokenNum(), wipeTransactionBody, ResponseCodeEnum.SUCCESS,
                newTotalSupply, wipeTokenTransfer);

        // process all record items in a single file
        parseRecordItemsAndCommit(List.of(
                new RecordItem(createTransaction, createTransactionRecord),
                new RecordItem(associateTransaction, associateRecord),
                new RecordItem(wipeTransaction, wipeRecord)));

        // Verify token, tokenAccount and tokenTransfer
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, newTotalSupply);
        assertTokenAccountInRepository(TOKEN_ID, PAYER, true, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    private void insertAndParseTransaction(Transaction transaction, long timestamp, long newTotalSupply,
                                           List<AssessedCustomFee> assessedCustomFees, List<Long> serialNumbers,
                                           TokenTransferList... tokenTransferLists) {
        TransactionBody transactionBody = getTransactionBody(transaction);

        var transactionRecord = createTransactionRecord(timestamp, TOKEN_ID.getTokenNum(),
                transactionBody, ResponseCodeEnum.SUCCESS, newTotalSupply, assessedCustomFees, serialNumbers,
                Arrays.asList(tokenTransferLists));

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, timestamp, null);
    }

    private void insertAndParseTransaction(Transaction transaction, long timestamp, long newTotalSupply,
                                           List<Long> serialNumbers, TokenTransferList... tokenTransferLists) {
        insertAndParseTransaction(transaction, timestamp, newTotalSupply, Lists.emptyList(), serialNumbers,
                tokenTransferLists);
    }

    private void insertAndParseTransaction(Transaction transaction, long timestamp,long newTotalSupply) {
        insertAndParseTransaction(transaction, timestamp, newTotalSupply, Lists.emptyList());
    }

    private void insertAndParseTransaction(Transaction transaction, long timestamp, long newTotalSupply,
                                           TokenTransferList... tokenTransferLists) {
        insertAndParseTransaction(transaction, timestamp, newTotalSupply, Lists.emptyList(), tokenTransferLists);
    }

    private void insertAndParseTransactionWithCustomFees(Transaction transaction, long timestamp, long newTotalSupply,
                                                         List<AssessedCustomFee> assessedCustomFees,
                                                         TokenTransferList... tokenTransferLists) {
        insertAndParseTransaction(transaction, timestamp, newTotalSupply, assessedCustomFees, Lists.emptyList(),
                tokenTransferLists);
    }

    private Transaction tokenCreateTransaction(TokenType tokenType, boolean setFreezeKey, boolean setKycKey,
                                               String symbol, List<CustomFee> customFees) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                    .setDecimals(1000)
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFreezeDefault(false)
                    .setMemo(TOKEN_CREATE_MEMO)
                    .setName(symbol + "_token_name")
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSupplyType(TokenSupplyType.INFINITE)
                    .setSymbol(symbol)
                    .setTokenType(tokenType)
                    .setTreasury(PAYER)
                    .setWipeKey(TOKEN_REF_KEY)
                    .addAllCustomFees(convertCustomFees(customFees));

            if (tokenType == TokenType.FUNGIBLE_COMMON) {
                builder.getTokenCreationBuilder().setInitialSupply(INITIAL_SUPPLY);
            }

            if (setFreezeKey) {
                builder.getTokenCreationBuilder().setFreezeKey(TOKEN_REF_KEY);
            }

            if (setKycKey) {
                builder.getTokenCreationBuilder().setKycKey(TOKEN_REF_KEY);
            }
        });
    }

    private Transaction tokenCreateTransaction(TokenType tokenType, boolean setFreezeKey, boolean setKycKey,
                                               String symbol) {
        return tokenCreateTransaction(tokenType, setFreezeKey, setKycKey, symbol, Lists.emptyList());
    }

    private Transaction tokenUpdateTransaction(TokenID tokenID, String symbol, String memo, Key newKey,
                                               AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenUpdateBuilder()
                .setAdminKey(newKey)
                .setAutoRenewAccount(accountID)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(TOKEN_UPDATE_AUTO_RENEW_PERIOD))
                .setExpiry(EXPIRY_TIMESTAMP)
                .setFeeScheduleKey(newKey)
                .setFreezeKey(newKey)
                .setKycKey(newKey)
                .setMemo(StringValue.of(memo))
                .setName(symbol + "_update_name")
                .setSupplyKey(newKey)
                .setSymbol(symbol)
                .setToken(tokenID)
                .setTreasury(accountID)
                .setWipeKey(newKey)
        );
    }

    private Transaction tokenAssociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenAssociateBuilder()
                .setAccount(accountID)
                .addAllTokens(tokenIDs));
    }

    private Transaction tokenDissociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder -> builder.getTokenDissociateBuilder()
                .setAccount(accountID)
                .addAllTokens(tokenIDs));
    }

    private Transaction tokenDeleteTransaction(TokenID tokenID) {
        return buildTransaction(builder -> builder.getTokenDeletionBuilder()
                .setToken(tokenID));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, boolean freeze) {
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(builder -> builder.getTokenFreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenUnfreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenID, boolean kyc) {
        Transaction transaction;
        if (kyc) {
            transaction = buildTransaction(builder -> builder.getTokenGrantKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenRevokeKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER));
        }

        return transaction;
    }

    private Transaction tokenSupplyTransaction(TokenID tokenID, TokenType tokenType, boolean mint, long amount,
                                               List<Long> serialNumbers) {
        Transaction transaction = null;
        if (mint) {
            transaction = buildTransaction(builder -> {
                builder.getTokenMintBuilder()
                        .setToken(tokenID);
                if (tokenType == TokenType.FUNGIBLE_COMMON) {
                    builder.getTokenMintBuilder().setAmount(amount);
                } else {
                    builder.getTokenMintBuilder().addAllMetadata(Collections
                            .nCopies(serialNumbers.size(), ByteString.copyFromUtf8(METADATA)));
                }
            });
        } else {
            transaction = buildTransaction(builder -> {
                builder.getTokenBurnBuilder()
                        .setToken(tokenID);
                if (tokenType == TokenType.FUNGIBLE_COMMON) {
                    builder.getTokenBurnBuilder().setAmount(amount);
                } else {
                    builder.getTokenBurnBuilder()
                            .addAllSerialNumbers(serialNumbers);
                }
            });
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(TokenID tokenID, TokenType tokenType, long amount,
                                             List<Long> serialNumbers) {
        return buildTransaction(builder -> {
            builder.getTokenWipeBuilder()

                    .setToken(tokenID)
                    .setAccount(PAYER)
                    .build();
            if (tokenType == TokenType.FUNGIBLE_COMMON) {
                builder.getTokenWipeBuilder().setAmount(amount);
            } else {
                builder.getTokenWipeBuilder().addAllSerialNumbers(serialNumbers);
            }
        });
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(TransactionBody.Builder::getCryptoTransferBuilder);
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode,
                                                      long newTotalSupply,
                                                      List<AssessedCustomFee> assessedCustomFees,
                                                      List<Long> serialNumbers,
                                                      List<TokenTransferList> tokenTransferLists) {
        var receipt = TransactionReceipt.newBuilder()
                .setStatus(responseCode)
                .setTokenID(TokenID.newBuilder().setTokenNum(tokenNum).build())
                .setNewTotalSupply(newTotalSupply)
                .addAllSerialNumbers(serialNumbers);

        return buildTransactionRecord(recordBuilder -> {
            // note the custom fee crypto transfers and token transfers are not added to the transaction record since
            // the test only has to verify the assessed custom fees get ingested.
            recordBuilder
                    .setReceipt(receipt)
                    .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp))
                    .addAllTokenTransferLists(tokenTransferLists)
                    .addAllAssessedCustomFees(convertAssessedCustomFees(assessedCustomFees));
        }, transactionBody, responseCode.getNumber());
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode,
                                                      long newTotalSupply,
                                                      List<Long> serialNumbers,
                                                      List<TokenTransferList> tokenTransferLists) {
        return createTransactionRecord(consensusTimestamp, tokenNum, transactionBody, responseCode, newTotalSupply,
                Lists.emptyList(), serialNumbers, tokenTransferLists);
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode, long newTotalSupply) {
        return createTransactionRecord(consensusTimestamp, tokenNum, transactionBody, responseCode, newTotalSupply,
                Lists.emptyList(), Lists.emptyList());
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, long tokenNum,
                                                      TransactionBody transactionBody,
                                                      ResponseCodeEnum responseCode, long newTotalSupply,
                                                      TokenTransferList... tokenTransferLists) {
        return createTransactionRecord(consensusTimestamp, tokenNum, transactionBody, responseCode, newTotalSupply,
                Lists.emptyList(), Arrays.asList(tokenTransferLists));
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply,
                                         byte[] keyData, String... keyFields) {
        // clear cache for PgCopy scenarios which don't utilize it
        cacheManager.getCache("tokens").clear();

        Optional<Token> tokenOptional = tokenRepository.findById(new TokenId(EntityId.of(tokenID)));
        if (present) {
            assertThat(tokenOptional)
                    .get()
                    .returns(createdTimestamp, from(Token::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Token::getModifiedTimestamp))
                    .returns(symbol, from(Token::getSymbol))
                    .returns(totalSupply, from(Token::getTotalSupply));
            if (keyFields.length != 0) {
                assertThat(tokenOptional)
                        .get()
                        .extracting(keyFields)
                        .containsExactlyElementsOf(Arrays.stream(keyFields)
                                .map((v) -> keyData)
                                .collect(Collectors.toList())
                        );
            }
        } else {
            assertThat(tokenOptional).isNotPresent();
        }
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply) {
        assertTokenInRepository(tokenID, present, createdTimestamp, modifiedTimestamp, symbol, totalSupply, null);
    }

    private void assertNftInRepository(TokenID tokenID, long serialNumber, boolean present, long createdTimestamp,
                                       long modifiedTimestamp, byte[] metadata, EntityId accountId, boolean deleted) {
        Optional<Nft> nftOptional = nftRepository.findById(new NftId(serialNumber, EntityId.of(tokenID)));
        if (present) {
            assertThat(nftOptional)
                    .get()
                    .returns(createdTimestamp, from(Nft::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Nft::getModifiedTimestamp))
                    .returns(metadata, from(Nft::getMetadata))
                    .returns(accountId, from(Nft::getAccountId))
                    .returns(deleted, from(Nft::isDeleted));
        } else {
            assertThat(nftOptional).isNotPresent();
        }
    }

    private void assertTokenAccountInRepository(TokenID tokenID, AccountID accountId, boolean present,
                                                long createdTimestamp, long modifiedTimestamp, boolean associated,
                                                TokenFreezeStatusEnum frozenStatus, TokenKycStatusEnum kycStatus) {
        // clear cache for PgCopy scenarios which don't utilize it
        cacheManager.getCache("tokenaccounts").clear();

        Optional<TokenAccount> tokenAccountOptional = tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenID).getId(), EntityId.of(accountId).getId());
        if (present) {
            assertThat(tokenAccountOptional.get())
                    .returns(createdTimestamp, from(TokenAccount::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(TokenAccount::getModifiedTimestamp))
                    .returns(associated, from(TokenAccount::getAssociated))
                    .returns(frozenStatus, from(TokenAccount::getFreezeStatus))
                    .returns(kycStatus, from(TokenAccount::getKycStatus));
        } else {
            assertThat(tokenAccountOptional.isPresent()).isFalse();
        }
    }

    private void assertTokenTransferInRepository(TokenID tokenID, AccountID accountID, long consensusTimestamp,
                                                 long amount) {
        com.hedera.mirror.importer.domain.TokenTransfer tokenTransfer = tokenTransferRepository
                .findById(new com.hedera.mirror.importer.domain.TokenTransfer.Id(consensusTimestamp, EntityId
                        .of(tokenID), EntityId.of(accountID))).get();
        assertThat(tokenTransfer)
                .returns(amount, from(com.hedera.mirror.importer.domain.TokenTransfer::getAmount));
    }

    private void assertCustomFeesInDb(List<CustomFee> expected) {
        var actual = jdbcTemplate.query(CustomFeeWrapper.SELECT_QUERY, CustomFeeWrapper.ROW_MAPPER);
        assertThat(actual).map(CustomFeeWrapper::getCustomFee).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertAssessedCustomFeesInDb(List<AssessedCustomFee> expected) {
        var actual = jdbcTemplate.query(AssessedCustomFeeWrapper.SELECT_QUERY, AssessedCustomFeeWrapper.ROW_MAPPER);
        assertThat(actual)
                .map(AssessedCustomFeeWrapper::getAssessedCustomFee)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertNftTransferInRepository(long consensusTimestamp, long serialNumber, TokenID tokenID,
                                               AccountID receiverId, AccountID senderId) {
        EntityId receiver = receiverId != null ? EntityId.of(receiverId) : null;
        EntityId sender = senderId != null ? EntityId.of(senderId) : null;

        com.hedera.mirror.importer.domain.NftTransfer nftTransfer = nftTransferRepository
                .findById(new com.hedera.mirror.importer.domain.NftTransferId(consensusTimestamp, serialNumber,
                        EntityId
                                .of(tokenID))).get();
        assertThat(nftTransfer)
                .returns(receiver, from(com.hedera.mirror.importer.domain.NftTransfer::getReceiverAccountId))
                .returns(sender, from(com.hedera.mirror.importer.domain.NftTransfer::getSenderAccountId));
    }

    private void createTokenEntity(TokenID tokenID, TokenType tokenType, String symbol, long consensusTimestamp,
                                   boolean setFreezeKey, boolean setKycKey,
                                   List<CustomFee> customFees) {
        Transaction createTransaction = tokenCreateTransaction(tokenType, setFreezeKey, setKycKey, symbol, customFees);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TokenTransferList tokenTransfer = tokenType == TokenType.FUNGIBLE_COMMON ? tokenTransfer(tokenID, PAYER,
                INITIAL_SUPPLY) : TokenTransferList.getDefaultInstance();
        var createTransactionRecord = createTransactionRecord(consensusTimestamp, tokenID
                .getTokenNum(), createTransactionBody, ResponseCodeEnum.SUCCESS, INITIAL_SUPPLY, tokenTransfer);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void createTokenEntity(TokenID tokenID, TokenType tokenType, String symbol, long consensusTimestamp,
                                   boolean setFreezeKey, boolean setKycKey) {
        createTokenEntity(tokenID, tokenType, symbol, consensusTimestamp, setFreezeKey, setKycKey, Lists.emptyList());
    }

    private void createAndAssociateToken(TokenID tokenID, TokenType tokenType, String symbol, long createTimestamp,
                                         long associateTimestamp, AccountID accountID, boolean setFreezeKey,
                                         boolean setKycKey, long initialSupply) {
        createTokenEntity(tokenID, tokenType, symbol, createTimestamp, setFreezeKey, setKycKey);
        assertTokenInRepository(tokenID, true, createTimestamp, createTimestamp, symbol, initialSupply);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTransaction, associateTimestamp, initialSupply);

        assertTokenAccountInRepository(tokenID, accountID, true, associateTimestamp, associateTimestamp, true,
                setFreezeKey ? TokenFreezeStatusEnum.UNFROZEN : TokenFreezeStatusEnum.NOT_APPLICABLE,
                setKycKey ? TokenKycStatusEnum.REVOKED : TokenKycStatusEnum.NOT_APPLICABLE);
    }

    private void updateTokenFeeSchedule(TokenID tokenID, long consensusTimestamp, List<CustomFee> customFees) {
        Transaction transaction = buildTransaction(builder -> builder.getTokenFeeScheduleUpdateBuilder()
                .setTokenId(tokenID)
                .addAllCustomFees(convertCustomFees(customFees))
        );
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                builder -> builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)),
                transactionBody, ResponseCodeEnum.SUCCESS.getNumber());

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
    }

    private TokenTransferList tokenTransfer(TokenID tokenId, AccountID accountId, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(tokenId)
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(amount).build())
                .build();
    }

    private TokenTransferList nftTransfer(TokenID tokenId, AccountID receiverAccountId, AccountID senderAccountId,
                                          List<Long> serialNumbers) {
        TokenTransferList.Builder builder = TokenTransferList.newBuilder();
        builder.setToken(tokenId);
        for (Long serialNumber : serialNumbers) {
            NftTransfer.Builder nftTransferBuilder = NftTransfer.newBuilder()
                    .setSerialNumber(serialNumber);
            if (receiverAccountId != null) {
                nftTransferBuilder.setReceiverAccountID(receiverAccountId);
            }
            if (senderAccountId != null) {
                nftTransferBuilder.setSenderAccountID(senderAccountId);
            }
            builder.addNftTransfers(
                    nftTransferBuilder.build()
            );
        }
        return builder.build();
    }

    private static List<CustomFee> deletedDbCustomFees(long consensusTimestamp, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setId(new CustomFee.Id(consensusTimestamp, tokenId));
        return List.of(customFee);
    }

    private static List<CustomFee> nonEmptyCustomFees(long consensusTimestamp, EntityId tokenId) {
        CustomFee.Id id = new CustomFee.Id(consensusTimestamp, tokenId);

        CustomFee fixedFee1 = new CustomFee();
        fixedFee1.setAmount(11L);
        fixedFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1);
        fixedFee1.setId(id);

        CustomFee fixedFee2 = new CustomFee();
        fixedFee2.setAmount(12L);
        fixedFee2.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee2.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
        fixedFee2.setId(id);

        CustomFee fractionalFee = new CustomFee();
        fractionalFee.setAmount(13L);
        fractionalFee.setAmountDenominator(31L);
        fractionalFee.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fractionalFee.setMaximumAmount(100L);
        fractionalFee.setId(id);

        return List.of(fixedFee1, fixedFee2, fractionalFee);
    }

    private static Stream<Arguments> provideCustomFees() {
        return Stream.of(
                Arguments.of("empty custom fees", deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID)),
                Arguments.of("non-empty custom fees", nonEmptyCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID))
        );
    }

    private static Stream<Arguments> provideAssessedCustomFees() {
        // paid in HBAR
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(12505L);
        assessedCustomFee1.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_1, TRANSFER_TIMESTAMP));

        // paid in FEE_DOMAIN_TOKEN_ID
        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(8750L);
        assessedCustomFee2.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_2, TRANSFER_TIMESTAMP));
        assessedCustomFee2.setTokenId(FEE_DOMAIN_TOKEN_ID);

        List<AssessedCustomFee> assessedCustomFees = List.of(assessedCustomFee1, assessedCustomFee2);

        return Stream.of(
                Arguments.of("no assessed custom fees", Lists.emptyList()),
                Arguments.of("has assessed custom fees", assessedCustomFees)
        );
    }

    private List<com.hederahashgraph.api.proto.java.AssessedCustomFee> convertAssessedCustomFees(
            List<AssessedCustomFee> assessedCustomFees) {
        return assessedCustomFees.stream()
                .map(assessedCustomFee -> {
                    EntityId collectorAccountId = assessedCustomFee.getId().getCollectorAccountId();
                    var builder = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                            .setAmount(assessedCustomFee.getAmount())
                            .setFeeCollectorAccountId(convertAccountId(collectorAccountId));

                    if (assessedCustomFee.getTokenId() != null) {
                        builder.setTokenId(convertTokenId(assessedCustomFee.getTokenId()));
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    private com.hederahashgraph.api.proto.java.CustomFee convertCustomFee(CustomFee customFee) {
        var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
                .setFeeCollectorAccountId(convertAccountId(customFee.getCollectorAccountId()));

        if (customFee.getAmountDenominator() == null) {
            // fixed fee
            var fixedFee = FixedFee.newBuilder().setAmount(customFee.getAmount());
            if (customFee.getDenominatingTokenId() != null) {
                fixedFee.setDenominatingTokenId(convertTokenId(customFee.getDenominatingTokenId()));
            }

            protoCustomFee.setFixedFee(fixedFee).build();
        } else {
            // fractional fee
            long maximumAmount = customFee.getMaximumAmount() != null ? customFee.getMaximumAmount() : 0;
            protoCustomFee.setFractionalFee(FractionalFee.newBuilder()
                    .setFractionalAmount(Fraction.newBuilder()
                            .setNumerator(customFee.getAmount())
                            .setDenominator(customFee.getAmountDenominator())
                    )
                    .setMaximumAmount(maximumAmount)
                    .setMinimumAmount(customFee.getMinimumAmount())
                    .build()
            );
        }

        return protoCustomFee.build();
    }

    private List<com.hederahashgraph.api.proto.java.CustomFee> convertCustomFees(List<CustomFee> customFees) {
        return customFees.stream()
                .filter(customFee -> customFee.getAmount() != null)
                .map(this::convertCustomFee)
                .collect(Collectors.toList());
    }

    private AccountID convertAccountId(EntityId accountId) {
        return AccountID.newBuilder()
                .setShardNum(accountId.getShardNum())
                .setRealmNum(accountId.getRealmNum())
                .setAccountNum(accountId.getEntityNum())
                .build();
    }

    private TokenID convertTokenId(EntityId tokenId) {
        return TokenID.newBuilder()
                .setShardNum(tokenId.getShardNum())
                .setRealmNum(tokenId.getRealmNum())
                .setTokenNum(tokenId.getEntityNum())
                .build();
    }
}
