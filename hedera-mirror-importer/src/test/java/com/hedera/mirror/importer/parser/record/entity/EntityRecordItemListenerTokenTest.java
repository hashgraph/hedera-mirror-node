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

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
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
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.vladmihalcea.hibernate.type.util.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Resource;
import lombok.Builder;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AssessedCustomFee;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.CustomFeeWrapper;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenAccountId;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenPauseStatusEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {

    private static final long ASSOCIATE_TIMESTAMP = 5L;
    private static final long AUTO_RENEW_PERIOD = 30L;
    private static final long CREATE_TIMESTAMP = 1L;
    private static final Timestamp EXPIRY_TIMESTAMP = Timestamp.newBuilder().setSeconds(360L).build();
    private static final long EXPIRY_NS = EXPIRY_TIMESTAMP.getSeconds() * 1_000_000_000 + EXPIRY_TIMESTAMP.getNanos();
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_1 = EntityIdEndec.decode(1199, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_2 = EntityIdEndec.decode(1200, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_3 = EntityIdEndec.decode(1201, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_DOMAIN_TOKEN_ID = EntityIdEndec.decode(9800, EntityTypeEnum.TOKEN);
    private static final EntityId FEE_PAYER_1 = EntityIdEndec.decode(1500, EntityTypeEnum.ACCOUNT);
    private static final EntityId FEE_PAYER_2 = EntityIdEndec.decode(1501, EntityTypeEnum.ACCOUNT);
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

    @BeforeEach
    void before() {
        entityProperties.getPersist().setTokens(true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithAutoTokenAssociations(String name, List<CustomFee> customFees, boolean freezeDefault,
                                              boolean freezeKey, boolean kycKey, boolean pauseKey,
                                              List<TokenAccount> expectedTokenAccounts) {
        List<EntityId> autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(TokenAccount::getId)
                .map(TokenAccountId::getAccountId)
                .collect(Collectors.toList());
        tokenCreate(customFees, freezeDefault, freezeKey, kycKey, pauseKey, expectedTokenAccounts,
                autoAssociatedAccounts);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithoutAutoTokenAssociations(String name, List<CustomFee> customFees, boolean freezeDefault,
                                                 boolean freezeKey, boolean kycKey, boolean pauseKey,
                                                 List<TokenAccount> expectedTokenAccounts) {
        tokenCreate(customFees, freezeDefault, freezeKey, kycKey, pauseKey, expectedTokenAccounts, Lists.emptyList());
    }

    @Test
    void tokenCreateWithoutPersistence() {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isZero();
        assertCustomFeesInDb(Lists.emptyList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateNftArguments")
    void tokenCreateWithNfts(String name, List<CustomFee> customFees, boolean freezeDefault, boolean freezeKey,
                             boolean kycKey, boolean pauseKey, List<TokenAccount> expectedTokenAccounts) {
        // given
        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);
        // node, token, autorenew, and the number of accounts associated with the token (including the treasury)
        long expectedEntityCount = 3 + expectedTokenAccounts.size();
        List<EntityId> autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(TokenAccount::getId)
                .map(TokenAccountId::getAccountId)
                .collect(Collectors.toList());

        // when
        createTokenEntity(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, freezeDefault, freezeKey,
                kycKey, pauseKey, customFees, autoAssociatedAccounts);

        // then
        assertEquals(expectedEntityCount, entityRepository.count());
        assertEntity(expected);

        // verify token
        TokenPauseStatusEnum pauseStatus = pauseKey ? TokenPauseStatusEnum.UNPAUSED :
                TokenPauseStatusEnum.NOT_APPLICABLE;
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, 0, pauseStatus);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        assertCustomFeesInDb(customFees);
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void tokenAssociate() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenAssociateWithMissingToken() {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        // verify token account was not created
        assertTokenAccountNotInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP);
    }

    @Test
    void tokenDissociate() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction);

        EntityId tokenId = EntityId.of(TOKEN_ID);
        EntityId accountId = EntityId.of(PAYER2);
        TokenAccount expected = new TokenAccount(tokenId, accountId, dissociateTimeStamp);
        expected.setCreatedTimestamp(ASSOCIATE_TIMESTAMP);
        expected.setAssociated(false);
        expected.setAutomaticAssociation(false);
        expected.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        expected.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        assertThat(latestTokenAccount(TOKEN_ID, PAYER2)).get().isEqualTo(expected);
    }

    @Test
    void tokenDissociateDeletedFungibleToken() {
        // given
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER2,
                false, false, false, INITIAL_SUPPLY);

        long tokenDeleteTimestamp = 15L;
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 20L;
        TokenTransferList dissociateTransfer = tokenTransfer(TOKEN_ID, PAYER2, -10);
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction, builder ->
                builder.addTokenTransferLists(dissociateTransfer));

        // then
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, dissociateTimeStamp, SYMBOL, INITIAL_SUPPLY - 10);
        var expected = new TokenTransfer(dissociateTimeStamp, -10, EntityId.of(TOKEN_ID), EntityId.of(PAYER2));
        assertThat(tokenTransferRepository.findById(expected.getId())).get().isEqualTo(expected);
    }

    @Test
    void tokenDissociateDeletedNonFungibleToken() {
        // given
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, PAYER2,
                false, false, false, 0);

        // mint
        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);
        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder().
                    setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // transfer
        long transferTimestamp = 15L;
        TokenTransferList nftTransfer = nftTransfer(TOKEN_ID, PAYER2, PAYER, List.of(1L));
        insertAndParseTransaction(transferTimestamp, tokenTransferTransaction(),
                builder -> builder.addTokenTransferLists(nftTransfer));

        // delete
        long tokenDeleteTimestamp = 20L;
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        // dissociate
        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 25L;
        TokenTransferList dissociateTransfer = tokenTransfer(TOKEN_ID, PAYER2, -1);
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction, builder ->
                builder.addTokenTransferLists(dissociateTransfer));

        // then
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, dissociateTimeStamp, PAYER2, true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, PAYER, false);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, dissociateTimeStamp, SYMBOL, 1);
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(
                domainNftTransfer(mintTimestamp, PAYER, DEFAULT_ACCOUNT_ID, 1L, TOKEN_ID),
                domainNftTransfer(mintTimestamp, PAYER, DEFAULT_ACCOUNT_ID, 2L, TOKEN_ID),
                domainNftTransfer(transferTimestamp, PAYER2, PAYER, 1L, TOKEN_ID),
                domainNftTransfer(dissociateTimeStamp, DEFAULT_ACCOUNT_ID, PAYER2, 1L, TOKEN_ID)
        );
        assertThat(tokenTransferRepository.findAll()).isEmpty();
    }

    @Test
    void tokenDelete() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        long deleteTimeStamp = 10L;
        insertAndParseTransaction(deleteTimeStamp, deleteTransaction);

        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                true, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, deleteTimeStamp);
        assertEquals(5, entityRepository.count()); // Node, payer (treasury), token, autorenew, and payer2
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TokenType.class, names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenFeeScheduleUpdate(TokenType tokenType) {
        // given
        // create the token entity with empty custom fees
        createTokenEntity(TOKEN_ID, tokenType, SYMBOL, CREATE_TIMESTAMP, false, false, false);
        // update fee schedule
        long updateTimestamp = CREATE_TIMESTAMP + 10L;
        Entity expectedEntity = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);
        List<CustomFee> newCustomFees = nonEmptyCustomFees(updateTimestamp, DOMAIN_TOKEN_ID, tokenType);
        List<CustomFee> expectedCustomFees = Lists.newArrayList(deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID));
        expectedCustomFees.addAll(newCustomFees);
        long expectedSupply = tokenType == FUNGIBLE_COMMON ? INITIAL_SUPPLY : 0;

        // when
        updateTokenFeeSchedule(TOKEN_ID, updateTimestamp, newCustomFees);

        // then
        assertEntity(expectedEntity);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, expectedSupply);
        assertCustomFeesInDb(expectedCustomFees);
    }

    @Test
    void tokenUpdate() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                TOKEN_UPDATE_REF_KEY,
                PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(updateTimeStamp, transaction);

        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_UPDATE_REF_KEY, EntityId.of(PAYER2),
                TOKEN_UPDATE_AUTO_RENEW_PERIOD, false, EXPIRY_NS, TOKEN_UPDATE_MEMO, null, CREATE_TIMESTAMP,
                updateTimeStamp);
        assertEquals(5, entityRepository.count()); // Node, payer, token, old autorenew, and new autorenew
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, updateTimeStamp, newSymbol, INITIAL_SUPPLY,
                TOKEN_UPDATE_REF_KEY.toByteArray(), TokenPauseStatusEnum.NOT_APPLICABLE, "feeScheduleKey", "freezeKey",
                "kycKey", "supplyKey",
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
        insertAndParseTransaction(10L, transaction);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenPause() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, true, INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        long pauseTimeStamp = 15L;
        insertAndParseTransaction(pauseTimeStamp, transaction);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, pauseTimeStamp, SYMBOL, INITIAL_SUPPLY,
                TokenPauseStatusEnum.PAUSED);
    }

    @Test
    void tokenUnpause() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, true, INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        insertAndParseTransaction(15L, transaction);

        transaction = tokenPauseTransaction(TOKEN_ID, false);
        long unpauseTimeStamp = 20L;
        insertAndParseTransaction(unpauseTimeStamp, transaction);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, unpauseTimeStamp, SYMBOL, INITIAL_SUPPLY,
                TokenPauseStatusEnum.UNPAUSED);
    }

    @Test
    void nftUpdateTreasury() {
        // given
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0);

        long mintTimestamp = 10L;
        List<Long> serialNumbers = List.of(1L);
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, serialNumbers);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                serialNumbers);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder().
                    setNewTotalSupply(serialNumbers.size())
                    .addAllSerialNumbers(serialNumbers);
            builder.addTokenTransferLists(mintTransfer);
        });

        // when
        long updateTimestamp = 15L;
        TokenTransferList treasuryUpdateTransfer = nftTransfer(TOKEN_ID, PAYER2, PAYER,
                List.of(NftTransferId.WILDCARD_SERIAL_NUMBER));
        insertAndParseTransaction(
                updateTimestamp,
                buildTransaction(builder -> builder.getTokenUpdateBuilder()
                        .setToken(TOKEN_ID)
                        .setTreasury(PAYER2)),
                builder -> builder.addTokenTransferLists(treasuryUpdateTransfer)
        );

        // then
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(
                domainNftTransfer(mintTimestamp, PAYER, DEFAULT_ACCOUNT_ID, 1L, TOKEN_ID),
                domainNftTransfer(updateTimestamp, PAYER2, PAYER, 1L, TOKEN_ID)
        );
    }

    @Test
    void tokenAccountFreeze() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, true, false, false, INITIAL_SUPPLY);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(freezeTimeStamp, transaction);

        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, freezeTimeStamp, true,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountUnfreeze() {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, false, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTimeStamp, freezeTransaction);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTimeStamp, unfreezeTransaction);

        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, unfreezeTimeStamp, true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void tokenAccountGrantKyc() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, true, false, INITIAL_SUPPLY);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, transaction);

        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, grantTimeStamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true, false);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, transaction);

        // verify token account was not created when missing
        assertTokenAccountNotInRepository(TOKEN_ID, PAYER2);
    }

    @Test
    void tokenAccountRevokeKyc() {
        // create token with kyc revoked
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, grantTransaction);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimestamp = 333;
        insertAndParseTransaction(revokeTimestamp, revokeTransaction);

        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, revokeTimestamp, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);
    }

    @Test
    void tokenBurn() {
        // given
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, false, amount, null);

        // when
        insertAndParseTransaction(burnTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // then
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, burnTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, INITIAL_SUPPLY - amount);
    }

    @Test
    void tokenBurnNft() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder().
                    setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0,
                List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(0L);
            builder.addTokenTransferLists(burnTransfer);
        });

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(3L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(burnTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, PAYER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 0);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, burnTimestamp, METADATA.getBytes(), null, true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenBurnNftMissingNft() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                List.of(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer);
        });

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0,
                List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(0);
            builder.addTokenTransferLists(burnTransfer);
        });

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
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, amount);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, true, amount, null);
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY + amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);
    }

    @Test
    void tokenMintNfts() {
        // given
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP, PAYER2, false, false, false, 0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
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
        // given
        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 2,
                SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(1L)
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, false, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, false, mintTimestamp, mintTimestamp, METADATA
                .getBytes(), EntityId.of(PAYER), false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithoutAutoTokenAssociations(String name, List<AssessedCustomFee> assessedCustomFees,
                                                   List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithAutoTokenAssociations(String name, List<AssessedCustomFee> assessedCustomFees,
                                                List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, true);
    }

    @Test
    void nftTransfer() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0);

        long mintTimestamp1 = 20L;
        TokenTransferList mintTransfer1 = nftTransfer(TOKEN_ID, RECEIVER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_1));
        Transaction mintTransaction1 = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                List.of(SERIAL_NUMBER_1));

        insertAndParseTransaction(mintTimestamp1, mintTransaction1, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(1L)
                    .addSerialNumbers(SERIAL_NUMBER_1);
            builder.addTokenTransferLists(mintTransfer1);
        });

        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, RECEIVER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction2 = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                List.of(SERIAL_NUMBER_2));

        // Verify
        insertAndParseTransaction(mintTimestamp2, mintTransaction2, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(2L)
                    .addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer2);
        });

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimestamp = 40L;
        insertAndParseTransaction(transferTimestamp, transaction, builder -> {
            builder.addAllTokenTransferLists(List.of(transferList1, transferList2));
        });

        assertThat(nftTransferRepository.count()).isEqualTo(4L);
        assertNftTransferInRepository(mintTimestamp1, SERIAL_NUMBER_1, TOKEN_ID, RECEIVER, null);
        assertNftTransferInRepository(mintTimestamp2, SERIAL_NUMBER_2, TOKEN_ID, RECEIVER, null);
        assertNftTransferInRepository(transferTimestamp, 1L, TOKEN_ID, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimestamp, 2L, TOKEN_ID, RECEIVER, PAYER);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_1, true, mintTimestamp1, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(TOKEN_ID, SERIAL_NUMBER_2, true, mintTimestamp2, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void nftTransferMissingNft() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0);

        TokenID tokenID2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenID2, FUNGIBLE_COMMON, symbol2, 15L, false, false, false);

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenID2)
                .addNftTransfers(NftTransfer.newBuilder().setReceiverAccountID(RECEIVER).setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2).build())
                .build();

        long transferTimestamp = 25L;
        insertAndParseTransaction(transferTimestamp, transaction, builder -> {
            builder.addAllTokenTransferLists(List.of(transferList1, transferList2));
        });

        assertThat(nftTransferRepository.count()).isEqualTo(2L);
        assertNftTransferInRepository(transferTimestamp, 1L, tokenID2, RECEIVER, PAYER);
        assertNftTransferInRepository(transferTimestamp, 2L, tokenID2, RECEIVER, PAYER);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_1, false, transferTimestamp, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
        assertNftInRepository(tokenID2, SERIAL_NUMBER_2, false, transferTimestamp, transferTimestamp, METADATA
                .getBytes(), EntityId.of(RECEIVER), false);
    }

    @Test
    void tokenWipe() {
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransfer(TOKEN_ID, PAYER, transferAmount);
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount,
                Lists.emptyList());
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - wipeAmount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, INITIAL_SUPPLY - wipeAmount);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, wipeTimestamp, transferAmount);
    }

    @Test
    void tokenWipeNft() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0,
                SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(2L)
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L);
            builder.addTokenTransferLists(wipeTransfer);
        });

        // Verify
        assertThat(nftTransferRepository.count()).isEqualTo(3L);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_1, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(mintTimestamp, SERIAL_NUMBER_2, TOKEN_ID, PAYER, null);
        assertNftTransferInRepository(wipeTimestamp, SERIAL_NUMBER_1, TOKEN_ID, null, PAYER);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 1);
        assertNftInRepository(TOKEN_ID, 1L, true, mintTimestamp, wipeTimestamp, METADATA.getBytes(), null, true);
        assertNftInRepository(TOKEN_ID, 2L, true, mintTimestamp, mintTimestamp, METADATA.getBytes(), EntityId
                .of(PAYER), false);
    }

    @Test
    void tokenWipeWithMissingToken() {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, 100L, null);
        insertAndParseTransaction(10L, transaction);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenWipeNftMissingNft() {
        createAndAssociateToken(TOKEN_ID, NON_FUNGIBLE_UNIQUE, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, 0);

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, RECEIVER, List.of(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.addTokenTransferLists(wipeTransfer);
        });

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
        Transaction createTransaction = tokenCreateTransaction(FUNGIBLE_COMMON, false, false, false, SYMBOL);
        TokenTransferList createTokenTransfer = tokenTransfer(TOKEN_ID, PAYER2, INITIAL_SUPPLY);
        RecordItem createTokenRecordItem = getRecordItem(CREATE_TIMESTAMP, createTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(INITIAL_SUPPLY)
                    .setTokenID(TOKEN_ID);
            builder.addTokenTransferLists(createTokenTransfer);
        });

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        RecordItem associateRecordItem = getRecordItem(ASSOCIATE_TIMESTAMP, associateTransaction);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransfer(TOKEN_ID, PAYER2, transferAmount);
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount, null);
        RecordItem wipeRecordItem = getRecordItem(wipeTimestamp, wipeTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(newTotalSupply);
            builder.addTokenTransferLists(wipeTokenTransfer);
        });

        // process all record items in a single file
        parseRecordItemsAndCommit(List.of(createTokenRecordItem, associateRecordItem, wipeRecordItem));

        // Verify token, tokenAccount and tokenTransfer
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, newTotalSupply);
        assertTokenAccountInRepository(TOKEN_ID, PAYER2, ASSOCIATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, true,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, wipeTimestamp, transferAmount);
    }

    void tokenCreate(List<CustomFee> customFees, boolean freezeDefault, boolean freezeKey, boolean kycKey,
                     boolean pauseKey, List<TokenAccount> expectedTokenAccounts,
                     List<EntityId> autoAssociatedAccounts) {
        // given
        Entity expected = createEntity(DOMAIN_TOKEN_ID, TOKEN_REF_KEY, EntityId.of(PAYER), AUTO_RENEW_PERIOD,
                false, EXPIRY_NS, TOKEN_CREATE_MEMO, null, CREATE_TIMESTAMP, CREATE_TIMESTAMP);
        // node, token, autorenew, and the number of accounts associated with the token (including the treasury)
        long expectedEntityCount = 3 + expectedTokenAccounts.size();

        // when
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, freezeDefault, freezeKey,
                kycKey, pauseKey, customFees, autoAssociatedAccounts);

        // then
        assertEquals(expectedEntityCount, entityRepository.count());
        assertEntity(expected);

        // verify token
        TokenPauseStatusEnum pauseStatus = pauseKey ? TokenPauseStatusEnum.UNPAUSED :
                TokenPauseStatusEnum.NOT_APPLICABLE;
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY,
                pauseStatus);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertCustomFeesInDb(customFees);
        assertThat(tokenTransferRepository.count()).isEqualTo(1L);
    }

    void tokenTransfer(List<AssessedCustomFee> assessedCustomFees,
                       List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees,
                       boolean hasAutoTokenAssociations) {
        // given
        createAndAssociateToken(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP,
                PAYER2, false, false, false, INITIAL_SUPPLY);
        TokenID tokenId2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenId2, FUNGIBLE_COMMON, symbol2, 10L, false, false, false);

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-1000).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(1000).build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenId2)
                .addTransfers(AccountAmount.newBuilder().setAccountID(PAYER).setAmount(333).build())
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(-333).build())
                .build();
        List<TokenTransferList> transferLists = List.of(transferList1, transferList2);

        // token treasury associations <TOKEN_ID, PAYER> and <tokenId2, PAYER> are created in the token create
        // transaction and they are not auto associations; the two token transfers' <token, recipient> pairs are
        // <TOKEN_ID, accountId> and <tokenId2, PAYER>, since <tokenId2, PAYER> already exists, only
        // <TOKEN_ID accountId> will be auto associated
        var autoTokenAssociation = TokenAssociation.newBuilder().setAccountId(accountId).setTokenId(TOKEN_ID).build();

        var autoTokenAccount = new TokenAccount(EntityId.of(TOKEN_ID), EntityId.of(accountId), TRANSFER_TIMESTAMP);
        autoTokenAccount.setAssociated(true);
        autoTokenAccount.setAutomaticAssociation(true);
        autoTokenAccount.setCreatedTimestamp(TRANSFER_TIMESTAMP);
        autoTokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        autoTokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        List<TokenAccount> expectedAutoAssociatedTokenAccounts = hasAutoTokenAssociations ? List.of(autoTokenAccount) :
                Lists.emptyList();

        // when
        insertAndParseTransaction(TRANSFER_TIMESTAMP, transaction, builder -> {
            builder.addAllTokenTransferLists(transferLists)
                    .addAllAssessedCustomFees(protoAssessedCustomFees);
            if (hasAutoTokenAssociations) {
                builder.addAutomaticTokenAssociations(autoTokenAssociation);
            }
        });

        // then
        assertTokenTransferInRepository(TOKEN_ID, PAYER, TRANSFER_TIMESTAMP, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenTransferInRepository(tokenId2, PAYER, TRANSFER_TIMESTAMP, 333);
        assertTokenTransferInRepository(tokenId2, accountId, TRANSFER_TIMESTAMP, -333);
        assertAssessedCustomFeesInDb(assessedCustomFees);
        assertThat(tokenAccountRepository.findAll())
                .filteredOn(TokenAccount::getAutomaticAssociation)
                .containsExactlyInAnyOrderElementsOf(expectedAutoAssociatedTokenAccounts);
    }

    private RecordItem getRecordItem(long consensusTimestamp, Transaction transaction) {
        return getRecordItem(consensusTimestamp, transaction, builder -> {});
    }

    private RecordItem getRecordItem(long consensusTimestamp, Transaction transaction,
                                     Consumer<TransactionRecord.Builder> customBuilder) {
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(builder -> {
            builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));
            customBuilder.accept(builder);
        }, transactionBody, ResponseCodeEnum.SUCCESS.getNumber());

        return new RecordItem(transaction, transactionRecord);
    }

    private void insertAndParseTransaction(long consensusTimestamp, Transaction transaction) {
        insertAndParseTransaction(consensusTimestamp, transaction, builder -> {
        });
    }

    private void insertAndParseTransaction(long consensusTimestamp, Transaction transaction,
                                           Consumer<TransactionRecord.Builder> customBuilder) {
        parseRecordItemAndCommit(getRecordItem(consensusTimestamp, transaction, customBuilder));
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, consensusTimestamp, null);
    }

    private com.hedera.mirror.importer.domain.NftTransfer domainNftTransfer(long consensusTimestamp, AccountID receiver,
                                                                            AccountID sender, long serialNumber,
                                                                            TokenID token) {
        var nftTransfer = new com.hedera.mirror.importer.domain.NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, EntityId.of(token)));
        if (!receiver.equals(DEFAULT_ACCOUNT_ID)) {
            nftTransfer.setReceiverAccountId(EntityId.of(receiver));
        }
        if (!sender.equals(DEFAULT_ACCOUNT_ID)) {
            nftTransfer.setSenderAccountId(EntityId.of(sender));
        }
        return nftTransfer;
    }

    private Transaction tokenCreateTransaction(TokenType tokenType, boolean freezeDefault, boolean setFreezeKey,
                                               boolean setKycKey, boolean setPauseKey, String symbol,
                                               List<CustomFee> customFees) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                    .setDecimals(1000)
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFreezeDefault(freezeDefault)
                    .setMemo(TOKEN_CREATE_MEMO)
                    .setName(symbol + "_token_name")
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSupplyType(TokenSupplyType.INFINITE)
                    .setSymbol(symbol)
                    .setTokenType(tokenType)
                    .setTreasury(PAYER)
                    .setWipeKey(TOKEN_REF_KEY)
                    .addAllCustomFees(convertCustomFees(customFees));

            if (tokenType == FUNGIBLE_COMMON) {
                builder.getTokenCreationBuilder().setInitialSupply(INITIAL_SUPPLY);
            }

            if (setFreezeKey) {
                builder.getTokenCreationBuilder().setFreezeKey(TOKEN_REF_KEY);
            }

            if (setKycKey) {
                builder.getTokenCreationBuilder().setKycKey(TOKEN_REF_KEY);
            }

            if (setPauseKey) {
                builder.getTokenCreationBuilder().setPauseKey(TOKEN_REF_KEY);
            }
        });
    }

    private Transaction tokenCreateTransaction(TokenType tokenType, boolean setFreezeKey, boolean setKycKey,
                                               boolean setPauseKey, String symbol) {
        return tokenCreateTransaction(tokenType, false, setFreezeKey, setKycKey, setPauseKey, symbol,
                Lists.emptyList());
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
                .setPauseKey(newKey)
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
        return buildTransaction(builder -> builder.getTokenDeletionBuilder().setToken(tokenID));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, boolean freeze) {
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(builder -> builder.getTokenFreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenUnfreezeBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER2));
        }

        return transaction;
    }

    private Transaction tokenPauseTransaction(TokenID tokenID, boolean pause) {
        Transaction transaction = null;
        if (pause) {
            transaction = buildTransaction(builder -> builder.getTokenPauseBuilder()
                    .setToken(tokenID));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenUnpauseBuilder()
                    .setToken(tokenID));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenID, boolean kyc) {
        Transaction transaction;
        if (kyc) {
            transaction = buildTransaction(builder -> builder.getTokenGrantKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder -> builder.getTokenRevokeKycBuilder()
                    .setToken(tokenID)
                    .setAccount(PAYER2));
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
                if (tokenType == FUNGIBLE_COMMON) {
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
                if (tokenType == FUNGIBLE_COMMON) {
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
            if (tokenType == FUNGIBLE_COMMON) {
                builder.getTokenWipeBuilder().setAmount(amount);
            } else {
                builder.getTokenWipeBuilder().addAllSerialNumbers(serialNumbers);
            }
        });
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(TransactionBody.Builder::getCryptoTransferBuilder);
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply,
                                         byte[] keyData, TokenPauseStatusEnum pauseStatus, String... keyFields) {
        Optional<Token> tokenOptional = tokenRepository.findById(new TokenId(EntityId.of(tokenID)));
        if (present) {
            assertThat(tokenOptional)
                    .get()
                    .returns(createdTimestamp, from(Token::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Token::getModifiedTimestamp))
                    .returns(symbol, from(Token::getSymbol))
                    .returns(pauseStatus, from(Token::getPauseStatus))
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
        assertTokenInRepository(tokenID, present, createdTimestamp, modifiedTimestamp, symbol, totalSupply, null,
                TokenPauseStatusEnum.NOT_APPLICABLE);
    }

    private void assertTokenInRepository(TokenID tokenID, boolean present, long createdTimestamp,
                                         long modifiedTimestamp, String symbol, long totalSupply,
                                         TokenPauseStatusEnum pauseStatus) {
        assertTokenInRepository(tokenID, present, createdTimestamp, modifiedTimestamp, symbol, totalSupply, null,
                pauseStatus);
    }

    private void assertNftInRepository(TokenID tokenID, long serialNumber, boolean present, long createdTimestamp,
                                       long modifiedTimestamp, AccountID accountId, boolean deleted) {
        EntityId accountEntityId = accountId.equals(DEFAULT_ACCOUNT_ID) ? null : EntityId.of(accountId);
        assertNftInRepository(tokenID, serialNumber, present, createdTimestamp, modifiedTimestamp, METADATA.getBytes(),
                accountEntityId, deleted);
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
                    .returns(deleted, from(Nft::getDeleted));
        } else {
            assertThat(nftOptional).isNotPresent();
        }
    }

    private void assertTokenAccountInRepository(TokenID tokenID, AccountID accountId, long createdTimestamp,
                                                long modifiedTimestamp, boolean associated,
                                                TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        TokenAccount expected = new TokenAccount(EntityId.of(tokenID), EntityId.of(accountId), modifiedTimestamp);
        expected.setAssociated(associated);
        expected.setAutomaticAssociation(false);
        expected.setCreatedTimestamp(createdTimestamp);
        expected.setFreezeStatus(freezeStatus);
        expected.setKycStatus(kycStatus);

        assertThat(tokenAccountRepository.findById(expected.getId())).get().isEqualTo(expected);
    }

    private void assertTokenAccountNotInRepository(TokenID tokenId, AccountID accountId) {
        assertThat(latestTokenAccount(tokenId, accountId)).isNotPresent();
    }

    private void assertTokenAccountNotInRepository(TokenID tokenId, AccountID accountId, long modifiedTimestamp) {
        var id = new TokenAccountId(EntityId.of(tokenId), EntityId.of(accountId), modifiedTimestamp);
        assertThat(tokenAccountRepository.findById(id)).isNotPresent();
    }

    private void assertTokenTransferInRepository(TokenID tokenID, AccountID accountID, long consensusTimestamp,
                                                 long amount) {
        var expected = new TokenTransfer(consensusTimestamp, amount, EntityId.of(tokenID), EntityId.of(accountID));
        assertThat(tokenTransferRepository.findById(expected.getId()))
                .get()
                .isEqualTo(expected);
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

        var id = new NftTransferId(consensusTimestamp, serialNumber, EntityId.of(tokenID));
        assertThat(nftTransferRepository.findById(id))
                .get()
                .returns(receiver, from(com.hedera.mirror.importer.domain.NftTransfer::getReceiverAccountId))
                .returns(sender, from(com.hedera.mirror.importer.domain.NftTransfer::getSenderAccountId));
    }

    private void createTokenEntity(TokenID tokenId, TokenType tokenType, String symbol, long consensusTimestamp,
                                   boolean freezeDefault, boolean setFreezeKey, boolean setKycKey, boolean setPauseKey,
                                   List<CustomFee> customFees, List<EntityId> autoAssociatedAccounts) {
        var transaction = tokenCreateTransaction(tokenType, freezeDefault, setFreezeKey, setKycKey, setPauseKey,
                symbol, customFees);
        insertAndParseTransaction(consensusTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setTokenID(tokenId)
                    .setNewTotalSupply(INITIAL_SUPPLY);
            builder.addAllAutomaticTokenAssociations(autoAssociatedAccounts.stream()
                    .map(account -> TokenAssociation.newBuilder()
                            .setTokenId(tokenId)
                            .setAccountId(convertAccountId(account))
                            .build())
                    .collect(Collectors.toList()));
            if (tokenType == FUNGIBLE_COMMON) {
                builder.addTokenTransferLists(tokenTransfer(tokenId, PAYER, INITIAL_SUPPLY));
            }
        });
    }

    private void createTokenEntity(TokenID tokenID, TokenType tokenType, String symbol, long consensusTimestamp,
                                   boolean setFreezeKey, boolean setKycKey, boolean setPauseKey) {
        createTokenEntity(tokenID, tokenType, symbol, consensusTimestamp, false, setFreezeKey, setKycKey, setPauseKey,
                Lists.emptyList(), Lists.emptyList());
    }

    private void createAndAssociateToken(TokenID tokenID, TokenType tokenType, String symbol, long createTimestamp,
                                         long associateTimestamp, AccountID accountID, boolean setFreezeKey,
                                         boolean setKycKey, boolean setPauseKey, long initialSupply) {
        createTokenEntity(tokenID, tokenType, symbol, createTimestamp, setFreezeKey, setKycKey, setPauseKey);
        assertTokenInRepository(tokenID, true, createTimestamp, createTimestamp, symbol, initialSupply, setPauseKey ?
                TokenPauseStatusEnum.UNPAUSED : TokenPauseStatusEnum.NOT_APPLICABLE);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTimestamp, associateTransaction);

        assertTokenAccountInRepository(tokenID, accountID, associateTimestamp, associateTimestamp, true,
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
                .addTransfers(AccountAmount.newBuilder().setAccountID(accountId).setAmount(amount))
                .build();
    }

    private TokenTransferList nftTransfer(TokenID tokenId, AccountID receiverAccountId, AccountID senderAccountId,
                                          List<Long> serialNumbers) {
        TokenTransferList.Builder builder = TokenTransferList.newBuilder();
        builder.setToken(tokenId);
        for (Long serialNumber : serialNumbers) {
            NftTransfer.Builder nftTransferBuilder = NftTransfer.newBuilder().setSerialNumber(serialNumber);
            if (receiverAccountId != null) {
                nftTransferBuilder.setReceiverAccountID(receiverAccountId);
            }
            if (senderAccountId != null) {
                nftTransferBuilder.setSenderAccountID(senderAccountId);
            }
            builder.addNftTransfers(nftTransferBuilder);
        }
        return builder.build();
    }

    private Optional<TokenAccount> latestTokenAccount(TokenID tokenId, AccountID accountId) {
        return Lists.newArrayList(tokenAccountRepository.findAll())
                .stream()
                .filter(ta -> ta.getId().getTokenId().equals(EntityId.of(tokenId))
                        && ta.getId().getAccountId().equals(EntityId.of(accountId)))
                .max(Comparator.comparing(ta -> ta.getId().getModifiedTimestamp()));
    }

    private static List<CustomFee> deletedDbCustomFees(long consensusTimestamp, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setId(new CustomFee.Id(consensusTimestamp, tokenId));
        return List.of(customFee);
    }

    private static List<CustomFee> nonEmptyCustomFees(long consensusTimestamp, EntityId tokenId, TokenType tokenType) {
        List<CustomFee> customFees = new ArrayList<>();
        CustomFee.Id id = new CustomFee.Id(consensusTimestamp, tokenId);
        EntityId treasury = EntityId.of(PAYER);

        CustomFee fixedFee1 = new CustomFee();
        fixedFee1.setAmount(11L);
        fixedFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1);
        fixedFee1.setId(id);
        customFees.add(fixedFee1);

        CustomFee fixedFee2 = new CustomFee();
        fixedFee2.setAmount(12L);
        fixedFee2.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee2.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
        fixedFee2.setId(id);
        customFees.add(fixedFee2);

        CustomFee fixedFee3 = new CustomFee();
        fixedFee3.setAmount(13L);
        fixedFee3.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee3.setDenominatingTokenId(tokenId);
        fixedFee3.setId(id);
        customFees.add(fixedFee3);

        if (tokenType == FUNGIBLE_COMMON) {
            // fractional fees only apply for fungible tokens
            CustomFee fractionalFee1 = new CustomFee();
            fractionalFee1.setAmount(14L);
            fractionalFee1.setAmountDenominator(31L);
            fractionalFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_3);
            fractionalFee1.setMaximumAmount(100L);
            fractionalFee1.setNetOfTransfers(true);
            fractionalFee1.setId(id);
            customFees.add(fractionalFee1);

            CustomFee fractionalFee2 = new CustomFee();
            fractionalFee2.setAmount(15L);
            fractionalFee2.setAmountDenominator(32L);
            fractionalFee2.setCollectorAccountId(treasury);
            fractionalFee2.setMaximumAmount(110L);
            fractionalFee2.setNetOfTransfers(false);
            fractionalFee2.setId(id);
            customFees.add(fractionalFee2);
        } else {
            // royalty fees only apply for non-fungible tokens
            CustomFee royaltyFee1 = new CustomFee();
            royaltyFee1.setRoyaltyNumerator(14L);
            royaltyFee1.setRoyaltyDenominator(31L);
            royaltyFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_3);
            royaltyFee1.setId(id);
            customFees.add(royaltyFee1);

            // with fallback fee
            CustomFee royaltyFee2 = new CustomFee();
            royaltyFee2.setRoyaltyNumerator(15L);
            royaltyFee2.setRoyaltyDenominator(32L);
            royaltyFee2.setCollectorAccountId(treasury);
            // fallback fee in form of fixed fee
            royaltyFee2.setAmount(103L);
            royaltyFee2.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
            royaltyFee2.setId(id);
            customFees.add(royaltyFee2);
        }

        return customFees;
    }

    private static Stream<Arguments> provideTokenCreateFtArguments() {
        return provideTokenCreateArguments(FUNGIBLE_COMMON);
    }

    private static Stream<Arguments> provideTokenCreateNftArguments() {
        return provideTokenCreateArguments(NON_FUNGIBLE_UNIQUE);
    }

    private static Stream<Arguments> provideTokenCreateArguments(TokenType tokenType) {
        List<CustomFee> nonEmptyCustomFees = nonEmptyCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID, tokenType);
        EntityId treasury = EntityId.of(PAYER);
        // fractional fees only apply for FT, thus FEE_COLLECTOR_ACCOUNT_ID_3 (collector of a fractional fee for FT, and
        // a royalty fee in case of NFT) will be auto enabled only for FT custom fees
        List<EntityId> autoEnabledAccounts = tokenType == FUNGIBLE_COMMON ?
                List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2, FEE_COLLECTOR_ACCOUNT_ID_3) :
                List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2);

        return Stream.of(
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(List.of(treasury))
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID))
                        .customFeesDescription("empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .freezeKey(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .freezeDefault(true)
                        .freezeKey(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .kycKey(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .pauseKey(true)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments()
        );
    }

    private static Stream<Arguments> provideAssessedCustomFees() {
        // without effective payer account ids, this is prior to services 0.17.1
        // paid in HBAR
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(12505L);
        assessedCustomFee1.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee1.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_1, TRANSFER_TIMESTAMP));

        // paid in FEE_DOMAIN_TOKEN_ID
        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(8750L);
        assessedCustomFee2.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee2.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_2, TRANSFER_TIMESTAMP));
        assessedCustomFee2.setTokenId(FEE_DOMAIN_TOKEN_ID);
        List<AssessedCustomFee> assessedCustomFees = List.of(assessedCustomFee1, assessedCustomFee2);

        // build the corresponding protobuf assessed custom fee list
        var protoAssessedCustomFee1 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .setAmount(12505L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_1))
                .build();
        var protoAssessedCustomFee2 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .setAmount(8750L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_2))
                .setTokenId(convertTokenId(FEE_DOMAIN_TOKEN_ID))
                .build();
        var protoAssessedCustomFees = List.of(protoAssessedCustomFee1, protoAssessedCustomFee2);

        // with effective payer account ids
        // paid in HBAR, one effective payer
        AssessedCustomFee assessedCustomFee3 = new AssessedCustomFee();
        assessedCustomFee3.setAmount(12300L);
        assessedCustomFee3.setEffectivePayerEntityIds(List.of(FEE_PAYER_1));
        assessedCustomFee3.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_1, TRANSFER_TIMESTAMP));

        // paid in FEE_DOMAIN_TOKEN_ID, two effective payers
        AssessedCustomFee assessedCustomFee4 = new AssessedCustomFee();
        assessedCustomFee4.setAmount(8790L);
        assessedCustomFee4.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_2, TRANSFER_TIMESTAMP));
        assessedCustomFee4.setEffectivePayerEntityIds(List.of(FEE_PAYER_1, FEE_PAYER_2));
        assessedCustomFee4.setTokenId(FEE_DOMAIN_TOKEN_ID);
        List<AssessedCustomFee> assessedCustomFeesWithPayers = List.of(assessedCustomFee3, assessedCustomFee4);

        // build the corresponding protobuf assessed custom fee list, with effective payer account ids
        var protoAssessedCustomFee3 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .addAllEffectivePayerAccountId(List.of(convertAccountId(FEE_PAYER_1)))
                .setAmount(12300L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_1))
                .build();
        var protoAssessedCustomFee4 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .addAllEffectivePayerAccountId(List.of(convertAccountId(FEE_PAYER_1), convertAccountId(FEE_PAYER_2)))
                .setAmount(8790L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_2))
                .setTokenId(convertTokenId(FEE_DOMAIN_TOKEN_ID))
                .build();
        var protoAssessedCustomFeesWithPayers = List.of(protoAssessedCustomFee3, protoAssessedCustomFee4);

        return Stream.of(
                Arguments.of("no assessed custom fees", Lists.emptyList(), Lists.emptyList()),
                Arguments.of("has assessed custom fees without effective payer account ids", assessedCustomFees,
                        protoAssessedCustomFees),
                Arguments.of("has assessed custom fees with effective payer account ids", assessedCustomFeesWithPayers,
                        protoAssessedCustomFeesWithPayers)
        );
    }

    private com.hederahashgraph.api.proto.java.CustomFee convertCustomFee(CustomFee customFee) {
        var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
                .setFeeCollectorAccountId(convertAccountId(customFee.getCollectorAccountId()));

        if (customFee.getAmountDenominator() != null) {
            // fractional fee
            long maximumAmount = customFee.getMaximumAmount() != null ? customFee.getMaximumAmount() : 0;
            protoCustomFee.setFractionalFee(
                    FractionalFee.newBuilder()
                            .setFractionalAmount(
                                    Fraction.newBuilder()
                                            .setNumerator(customFee.getAmount())
                                            .setDenominator(customFee.getAmountDenominator())
                            )
                            .setMaximumAmount(maximumAmount)
                            .setMinimumAmount(customFee.getMinimumAmount())
                            .setNetOfTransfers(customFee.getNetOfTransfers())
            );
        } else if (customFee.getRoyaltyDenominator() != null) {
            // royalty fee
            RoyaltyFee.Builder royaltyFee = RoyaltyFee.newBuilder()
                    .setExchangeValueFraction(
                            Fraction.newBuilder()
                                    .setNumerator(customFee.getRoyaltyNumerator())
                                    .setDenominator(customFee.getRoyaltyDenominator())
                    );
            if (customFee.getAmount() != null) {
                royaltyFee.setFallbackFee(convertFixedFee(customFee));
            }

            protoCustomFee.setRoyaltyFee(royaltyFee);
        } else {
            // fixed fee
            protoCustomFee.setFixedFee(convertFixedFee(customFee));
        }

        return protoCustomFee.build();
    }

    private FixedFee.Builder convertFixedFee(CustomFee customFee) {
        FixedFee.Builder fixedFee = FixedFee.newBuilder().setAmount(customFee.getAmount());
        EntityId denominatingTokenId = customFee.getDenominatingTokenId();
        if (denominatingTokenId != null) {
            if (denominatingTokenId.equals(customFee.getId().getTokenId())) {
                fixedFee.setDenominatingTokenId(TokenID.getDefaultInstance());
            } else {
                fixedFee.setDenominatingTokenId(convertTokenId(denominatingTokenId));
            }
        }

        return fixedFee;
    }

    private List<com.hederahashgraph.api.proto.java.CustomFee> convertCustomFees(List<CustomFee> customFees) {
        return customFees.stream()
                .filter(customFee -> customFee.getAmount() != null || customFee.getRoyaltyDenominator() != null)
                .map(this::convertCustomFee)
                .collect(Collectors.toList());
    }

    private static AccountID convertAccountId(EntityId accountId) {
        return AccountID.newBuilder()
                .setShardNum(accountId.getShardNum())
                .setRealmNum(accountId.getRealmNum())
                .setAccountNum(accountId.getEntityNum())
                .build();
    }

    private static TokenID convertTokenId(EntityId tokenId) {
        return TokenID.newBuilder()
                .setShardNum(tokenId.getShardNum())
                .setRealmNum(tokenId.getRealmNum())
                .setTokenNum(tokenId.getEntityNum())
                .build();
    }

    @Builder
    static class TokenCreateArguments {
        List<EntityId> autoEnabledAccounts;
        long createdTimestamp;
        List<CustomFee> customFees;
        String customFeesDescription;
        boolean freezeDefault;
        boolean freezeKey;
        @Builder.Default
        TokenFreezeStatusEnum freezeStatus = TokenFreezeStatusEnum.NOT_APPLICABLE;
        boolean kycKey;
        @Builder.Default
        TokenKycStatusEnum kycStatus = TokenKycStatusEnum.NOT_APPLICABLE;
        boolean pauseKey;
        EntityId tokenId;

        public Arguments toArguments() {
            String description = StringUtils.join(
                    ", ",
                    customFeesDescription,
                    freezeDefault ? "freezeDefault false" : "freezeDefault true",
                    freezeKey ? "has freezeKey" : "no freezeKey",
                    kycKey ? "has kycKey" : "no kycKey"
            );
            List<TokenAccount> tokenAccounts = autoEnabledAccounts.stream()
                    .map(account -> {
                        TokenAccount tokenAccount = new TokenAccount(tokenId, account, createdTimestamp);
                        tokenAccount.setAssociated(true);
                        tokenAccount.setAutomaticAssociation(false);
                        tokenAccount.setCreatedTimestamp(createdTimestamp);
                        tokenAccount.setFreezeStatus(freezeStatus);
                        tokenAccount.setKycStatus(kycStatus);
                        return tokenAccount;
                    })
                    .collect(Collectors.toList());

            return Arguments.of(description, customFees, freezeDefault, freezeKey, kycKey, pauseKey, tokenAccounts);
        }
    }
}
