/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record.entity;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.domain.token.NftTransfer.WILDCARD_SERIAL_NUMBER;
import static com.hedera.mirror.importer.TestUtils.toEntityTransactions;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import com.google.protobuf.StringValue;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AssessedCustomFeeWrapper;
import com.hedera.mirror.importer.domain.CustomFeeWrapper;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {

    private static final long ASSOCIATE_TIMESTAMP = 5L;
    private static final long AUTO_RENEW_PERIOD = 30L;
    private static final long CREATE_TIMESTAMP = 1L;
    private static final long ALLOWANCE_TIMESTAMP = CREATE_TIMESTAMP + 1L;
    private static final Timestamp EXPIRY_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(360L).build();
    private static final long EXPIRY_NS = EXPIRY_TIMESTAMP.getSeconds() * 1_000_000_000 + EXPIRY_TIMESTAMP.getNanos();
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_1 = EntityIdEndec.decode(1199, ACCOUNT);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_2 = EntityIdEndec.decode(1200, ACCOUNT);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_3 = EntityIdEndec.decode(1201, ACCOUNT);
    private static final EntityId FEE_DOMAIN_TOKEN_ID = EntityIdEndec.decode(9800, EntityType.TOKEN);
    private static final EntityId FEE_PAYER_1 = EntityIdEndec.decode(1500, ACCOUNT);
    private static final EntityId FEE_PAYER_2 = EntityIdEndec.decode(1501, ACCOUNT);
    private static final long INITIAL_SUPPLY = 1_000_000L;
    private static final byte[] METADATA = "METADATA".getBytes();
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
    private static final EntityId PAYER_ACCOUNT_ID = EntityId.of(PAYER);
    private static final byte[] TRANSFER_SIGNATURE = Bytes.fromHexString(
                    "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            .toArray();

    private final ContractLogRepository contractLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenRepository tokenRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TransactionRepository transactionRepository;

    private static List<CustomFee> deletedDbCustomFees(long consensusTimestamp, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setId(new CustomFee.Id(consensusTimestamp, tokenId));
        customFee.setAllCollectorsAreExempt(false);
        return List.of(customFee);
    }

    private static List<CustomFee> nonEmptyCustomFees(long consensusTimestamp, EntityId tokenId, TokenType tokenType) {
        List<CustomFee> customFees = new ArrayList<>();
        CustomFee.Id id = new CustomFee.Id(consensusTimestamp, tokenId);
        EntityId treasury = PAYER_ACCOUNT_ID;

        CustomFee fixedFee1 = new CustomFee();
        fixedFee1.setAmount(11L);
        fixedFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1);
        fixedFee1.setId(id);
        fixedFee1.setAllCollectorsAreExempt(false);
        customFees.add(fixedFee1);

        CustomFee fixedFee2 = new CustomFee();
        fixedFee2.setAmount(12L);
        fixedFee2.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee2.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
        fixedFee2.setId(id);
        fixedFee2.setAllCollectorsAreExempt(false);
        customFees.add(fixedFee2);

        CustomFee fixedFee3 = new CustomFee();
        fixedFee3.setAmount(13L);
        fixedFee3.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee3.setDenominatingTokenId(tokenId);
        fixedFee3.setId(id);
        fixedFee3.setAllCollectorsAreExempt(true);
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
            fractionalFee1.setAllCollectorsAreExempt(false);
            customFees.add(fractionalFee1);

            CustomFee fractionalFee2 = new CustomFee();
            fractionalFee2.setAmount(15L);
            fractionalFee2.setAmountDenominator(32L);
            fractionalFee2.setCollectorAccountId(treasury);
            fractionalFee2.setMaximumAmount(110L);
            fractionalFee2.setNetOfTransfers(false);
            fractionalFee2.setId(id);
            fractionalFee2.setAllCollectorsAreExempt(true);
            customFees.add(fractionalFee2);
        } else {
            // royalty fees only apply for non-fungible tokens
            CustomFee royaltyFee1 = new CustomFee();
            royaltyFee1.setRoyaltyNumerator(14L);
            royaltyFee1.setRoyaltyDenominator(31L);
            royaltyFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_3);
            royaltyFee1.setId(id);
            royaltyFee1.setAllCollectorsAreExempt(false);
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
            royaltyFee2.setAllCollectorsAreExempt(true);
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
        EntityId treasury = PAYER_ACCOUNT_ID;
        // fractional fees only apply for FT, thus FEE_COLLECTOR_ACCOUNT_ID_3 (collector of a fractional fee for FT, and
        // a royalty fee in case of NFT) will be auto enabled only for FT custom fees
        List<EntityId> autoEnabledAccounts = tokenType == FUNGIBLE_COMMON
                ? List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2, FEE_COLLECTOR_ACCOUNT_ID_3)
                : List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2);

        List<Long> autoEnabledAccountBalances =
                tokenType == FUNGIBLE_COMMON ? List.of(1000000L, 0L, 0L) : List.of(0L, 0L, 0L);
        return Stream.of(
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(List.of(treasury))
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(deletedDbCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID))
                        .customFeesDescription("empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
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
                        .balances(autoEnabledAccountBalances)
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
                        .balances(autoEnabledAccountBalances)
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
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(nonEmptyCustomFees)
                        .customFeesDescription("non-empty custom fees")
                        .pauseKey(true)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments());
    }

    private static Stream<Arguments> provideTokenUpdateArguments() {
        return Stream.of(
                Arguments.of("new auto renew account", PAYER2, PAYER2.getAccountNum()),
                Arguments.of(
                        "clear auto renew account",
                        AccountID.newBuilder().setAccountNum(0).build(),
                        0L),
                Arguments.of("keep auto renew account", null, PAYER.getAccountNum()));
    }

    private static Stream<Arguments> provideAssessedCustomFees() {
        // without effective payer account ids, this is prior to services 0.17.1
        // paid in HBAR
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(12505L);
        assessedCustomFee1.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee1.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_1, TRANSFER_TIMESTAMP));
        assessedCustomFee1.setPayerAccountId(PAYER_ACCOUNT_ID);

        // paid in FEE_DOMAIN_TOKEN_ID
        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(8750L);
        assessedCustomFee2.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee2.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_2, TRANSFER_TIMESTAMP));
        assessedCustomFee2.setTokenId(FEE_DOMAIN_TOKEN_ID);
        assessedCustomFee2.setPayerAccountId(PAYER_ACCOUNT_ID);
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
        assessedCustomFee3.setEffectivePayerAccountIds(List.of(FEE_PAYER_1.getId()));
        assessedCustomFee3.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_1, TRANSFER_TIMESTAMP));
        assessedCustomFee3.setPayerAccountId(PAYER_ACCOUNT_ID);

        // paid in FEE_DOMAIN_TOKEN_ID, two effective payers
        AssessedCustomFee assessedCustomFee4 = new AssessedCustomFee();
        assessedCustomFee4.setAmount(8790L);
        assessedCustomFee4.setId(new AssessedCustomFee.Id(FEE_COLLECTOR_ACCOUNT_ID_2, TRANSFER_TIMESTAMP));
        assessedCustomFee4.setEffectivePayerAccountIds(List.of(FEE_PAYER_1.getId(), FEE_PAYER_2.getId()));
        assessedCustomFee4.setTokenId(FEE_DOMAIN_TOKEN_ID);
        assessedCustomFee4.setPayerAccountId(PAYER_ACCOUNT_ID);
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
                Arguments.of("no assessed custom fees", Collections.emptyList(), Collections.emptyList()),
                Arguments.of(
                        "has assessed custom fees without effective payer account ids",
                        assessedCustomFees,
                        protoAssessedCustomFees),
                Arguments.of(
                        "has assessed custom fees with effective payer account ids",
                        assessedCustomFeesWithPayers,
                        protoAssessedCustomFeesWithPayers));
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

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEntityTransactions(true);
        entityProperties.getPersist().setTokens(true);
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setEntityTransactions(false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithAutoTokenAssociations(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            List<TokenAccount> expectedTokenAccounts) {
        List<EntityId> autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(t -> EntityId.of(t.getAccountId(), ACCOUNT))
                .collect(Collectors.toList());
        tokenCreate(
                customFees, freezeDefault, freezeKey, kycKey, pauseKey, expectedTokenAccounts, autoAssociatedAccounts);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithoutAutoTokenAssociations(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            List<TokenAccount> expectedTokenAccounts) {
        tokenCreate(
                customFees, freezeDefault, freezeKey, kycKey, pauseKey, expectedTokenAccounts, Collections.emptyList());
    }

    @Test
    void tokenCreateWithoutPersistence() {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false, false);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        assertThat(tokenTransferRepository.count()).isZero();
        assertCustomFeesInDb(Collections.emptyList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateNftArguments")
    void tokenCreateWithNfts(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            List<TokenAccount> expectedTokenAccounts) {
        // given
        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN_REF_KEY,
                PAYER.getAccountNum(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                null,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
        List<EntityId> autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(t -> EntityId.of(t.getAccountId(), ACCOUNT))
                .collect(Collectors.toList());

        // when
        createTokenEntity(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                customFees,
                autoAssociatedAccounts);

        // then
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        // verify token
        TokenPauseStatusEnum pauseStatus =
                pauseKey ? TokenPauseStatusEnum.UNPAUSED : TokenPauseStatusEnum.NOT_APPLICABLE;
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

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                ASSOCIATE_TIMESTAMP);
    }

    @Test
    void tokenAssociatePrecompile() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction, builder -> {
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED,
                ASSOCIATE_TIMESTAMP);

        assertContractResult(ASSOCIATE_TIMESTAMP, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenAssociateWithMissingToken() {
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        // verify token account was not created
        assertTokenAccountNotInRepository(TOKEN_ID, PAYER);
    }

    @Test
    void tokenDissociate() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction);

        EntityId tokenId = EntityId.of(TOKEN_ID);
        EntityId accountId = EntityId.of(PAYER2);

        var expected = TokenAccount.builder()
                .accountId(accountId.getId())
                .associated(false)
                .automaticAssociation(false)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .tokenId(tokenId.getId())
                .build();

        assertThat(tokenAccountRepository.findById(expected.getId())).get().isEqualTo(expected);
    }

    @Test
    void tokenDissociatePrecompile() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 10L;
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction, builder -> {
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                dissociateTimeStamp);

        assertContractResult(dissociateTimeStamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenDissociateDeletedFungibleToken() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long amount = 10;
        long tokenTransferTimestamp = 10L;
        var transfers = tokenTransferList(TOKEN_ID, accountAmount(PAYER, -amount), accountAmount(PAYER2, amount));
        insertAndParseTransaction(
                tokenTransferTimestamp,
                tokenTransferTransaction(),
                builder -> builder.addTokenTransferLists(transfers));

        long tokenDeleteTimestamp = tokenTransferTimestamp + 5L;
        var deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenDeleteTimestamp + 5L;
        var dissociateTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -amount));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfer));

        // then
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, dissociateTimeStamp, SYMBOL, INITIAL_SUPPLY - amount);
        var tokenTransferId = new TokenTransfer.Id(dissociateTimeStamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER2));
        var expectedDissociateTransfer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-amount)
                        .id(tokenTransferId)
                        .isApproval(false)
                        .payerAccountId(PAYER_ACCOUNT_ID)
                        .deletedTokenDissociate(false))
                .get();
        assertThat(tokenTransferRepository.findById(tokenTransferId)).get().isEqualTo(expectedDissociateTransfer);

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(PAYER2.getAccountNum())
                .associated(false)
                .automaticAssociation(false)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .balance(0)
                .tokenId(TOKEN_ID.getTokenNum())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
    }

    @Test
    void tokenDissociateDeletedNonFungibleToken() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        // mint
        long mintTimestamp = 10L;
        var mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        var mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);
        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        // transfer
        long transferTimestamp = mintTimestamp + 5L;
        var nftTransfer = nftTransfer(TOKEN_ID, PAYER2, PAYER, SERIAL_NUMBER_LIST);
        insertAndParseTransaction(
                transferTimestamp, tokenTransferTransaction(), builder -> builder.addTokenTransferLists(nftTransfer));

        // then
        var historyRange = Range.closedOpen(mintTimestamp, transferTimestamp);
        var nftHistory = new ArrayList<Nft>();
        nftHistory.add(nft1.toBuilder().timestampRange(historyRange).build());
        nftHistory.add(nft2.toBuilder().timestampRange(historyRange).build());
        // Current rows
        Stream.of(nft1, nft2).forEach(n -> {
            n.setAccountId(EntityId.of(PAYER2));
            n.setTimestampLower(transferTimestamp);
        });
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        // delete
        long tokenDeleteTimestamp = transferTimestamp + 5L;
        var deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        // dissociate
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenDeleteTimestamp + 5L;
        var dissociateTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -2));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfer));

        // then
        historyRange = Range.closedOpen(transferTimestamp, dissociateTimeStamp);
        nftHistory.add(nft1.toBuilder().timestampRange(historyRange).build());
        nftHistory.add(nft2.toBuilder().timestampRange(historyRange).build());
        Stream.of(nft1, nft2).forEach(n -> {
            n.setAccountId(null);
            n.setDeleted(true);
            n.setTimestampLower(dissociateTimeStamp);
        });
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, dissociateTimeStamp, SYMBOL, 0);
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(dissociateTimeStamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER2, -2, TOKEN_ID));
        assertThat(tokenTransferRepository.findAll()).isEmpty();

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(PAYER2.getAccountNum())
                .associated(false)
                .automaticAssociation(false)
                .balance(0)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .tokenId(TOKEN_ID.getTokenNum())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenDissociateExpiredFungibleToken(boolean isTreasuryTransferFirst) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long amount = 10;
        long tokenTransferTimestamp = 10L;
        var transfers = tokenTransferList(TOKEN_ID, accountAmount(PAYER, -amount), accountAmount(PAYER2, amount));
        insertAndParseTransaction(
                tokenTransferTimestamp,
                tokenTransferTransaction(),
                builder -> builder.addTokenTransferLists(transfers));

        // at some time the token has expired, later, when
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenTransferTimestamp + 10L;
        var dissociateTransfers = isTreasuryTransferFirst
                ? tokenTransferList(TOKEN_ID, accountAmount(PAYER, amount), accountAmount(PAYER2, -amount))
                : tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -amount), accountAmount(PAYER, amount));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfers));

        // then
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
        var expectedDissociateTransfers = List.of(
                domainBuilder
                        .tokenTransfer()
                        .customize(t -> t.amount(-amount)
                                .id(new TokenTransfer.Id(
                                        dissociateTimeStamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER2)))
                                .isApproval(false)
                                .payerAccountId(PAYER_ACCOUNT_ID))
                        .get(),
                domainBuilder
                        .tokenTransfer()
                        .customize(t -> t.amount(amount)
                                .id(new TokenTransfer.Id(dissociateTimeStamp, EntityId.of(TOKEN_ID), PAYER_ACCOUNT_ID))
                                .isApproval(false)
                                .payerAccountId(PAYER_ACCOUNT_ID))
                        .get());
        assertThat(tokenTransferRepository.findByConsensusTimestamp(dissociateTimeStamp))
                .containsExactlyInAnyOrderElementsOf(expectedDissociateTransfers);

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(PAYER2.getAccountNum())
                .associated(false)
                .automaticAssociation(false)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .balance(0)
                .tokenId(TOKEN_ID.getTokenNum())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
    }

    @Test
    void tokenDelete() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        long deleteTimeStamp = 10L;
        insertAndParseTransaction(deleteTimeStamp, deleteTransaction);

        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN_REF_KEY,
                PAYER.getAccountNum(),
                AUTO_RENEW_PERIOD,
                true,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                null,
                CREATE_TIMESTAMP,
                deleteTimeStamp);
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenFeeScheduleUpdate(TokenType tokenType) {
        // given
        // create the token entity with empty custom fees
        createTokenEntity(TOKEN_ID, tokenType, SYMBOL, CREATE_TIMESTAMP, false, false, false);
        // update fee schedule
        long updateTimestamp = CREATE_TIMESTAMP + 10L;
        Entity expectedEntity = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN_REF_KEY,
                PAYER.getAccountNum(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                null,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenUpdateArguments")
    void tokenUpdate(String name, AccountID autoRenewAccountId, Long expectedAutoRenewAccountId) {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID, newSymbol, TOKEN_UPDATE_MEMO, TOKEN_UPDATE_REF_KEY, autoRenewAccountId, PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(updateTimeStamp, transaction);

        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN_UPDATE_REF_KEY,
                expectedAutoRenewAccountId,
                TOKEN_UPDATE_AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_UPDATE_MEMO,
                null,
                CREATE_TIMESTAMP,
                updateTimeStamp);
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        assertTokenInRepository(
                TOKEN_ID,
                true,
                CREATE_TIMESTAMP,
                updateTimeStamp,
                newSymbol,
                INITIAL_SUPPLY,
                TOKEN_UPDATE_REF_KEY.toByteArray(),
                TokenPauseStatusEnum.NOT_APPLICABLE,
                "feeScheduleKey",
                "freezeKey",
                "kycKey",
                "supplyKey",
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
                AccountID.newBuilder().setAccountNum(2002).build(),
                PAYER2);
        insertAndParseTransaction(10L, transaction);

        // verify token was not created when missing
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenPause() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                true,
                INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        long pauseTimeStamp = 15L;
        insertAndParseTransaction(pauseTimeStamp, transaction);

        assertTokenInRepository(
                TOKEN_ID, true, CREATE_TIMESTAMP, pauseTimeStamp, SYMBOL, INITIAL_SUPPLY, TokenPauseStatusEnum.PAUSED);
    }

    @Test
    void tokenUnpause() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                true,
                INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        insertAndParseTransaction(15L, transaction);

        transaction = tokenPauseTransaction(TOKEN_ID, false);
        long unpauseTimeStamp = 20L;
        insertAndParseTransaction(unpauseTimeStamp, transaction);

        assertTokenInRepository(
                TOKEN_ID,
                true,
                CREATE_TIMESTAMP,
                unpauseTimeStamp,
                SYMBOL,
                INITIAL_SUPPLY,
                TokenPauseStatusEnum.UNPAUSED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftUpdateTreasury(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        var metadata = recordItemBuilder.bytes(12);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();
        var updateRecordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).setTreasury(PAYER2))
                .record(r -> r.clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER2)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(WILDCARD_SERIAL_NUMBER))))
                .build();
        var recordItems = List.of(mintRecordItem, updateRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        long mintTimestamp = mintRecordItem.getConsensusTimestamp();
        long updateTimestamp = updateRecordItem.getConsensusTimestamp();
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                updateTimestamp, domainNftTransfer(PAYER2, PAYER, WILDCARD_SERIAL_NUMBER, TOKEN_ID));
        assertTokenInRepository(
                TOKEN_ID, true, CREATE_TIMESTAMP, updateTimestamp, SYMBOL, 1, TokenPauseStatusEnum.NOT_APPLICABLE);

        var expectedNft = Nft.builder()
                .accountId(EntityId.of(PAYER2))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var expectedNftHistory = expectedNft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(expectedNft);
        assertThat(findHistory(Nft.class)).containsExactly(expectedNftHistory);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, true
                            true, false
                            false, true
                            false, false
                            """)
    void nftUpdateTreasuryWithNftStateChange(boolean explicitAssociation, boolean singleRecordFile) {
        // given
        var account = domainBuilder.entityId(ACCOUNT);
        var oldTreasury = domainBuilder.entityId(ACCOUNT);
        var newTreasury = domainBuilder.entityId(ACCOUNT);
        var tokenId = domainBuilder.entityId(TOKEN);
        var protoAccount =
                AccountID.newBuilder().setAccountNum(account.getEntityNum()).build();
        var protoOldTreasury =
                AccountID.newBuilder().setAccountNum(oldTreasury.getEntityNum()).build();
        var protoNewTreasury =
                AccountID.newBuilder().setAccountNum(newTreasury.getEntityNum()).build();
        var protoTokenId =
                TokenID.newBuilder().setTokenNum(tokenId.getEntityNum()).build();

        var recordItems = new ArrayList<RecordItem>();
        var tokenCreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.clearCustomFees()
                        .clearFreezeKey()
                        .clearKycKey()
                        .setInitialSupply(0L)
                        .setTokenType(NON_FUNGIBLE_UNIQUE)
                        .setTreasury(protoOldTreasury))
                .receipt(r -> r.setTokenID(protoTokenId))
                .record(r -> r.clearAutomaticTokenAssociations()
                        .addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setTokenId(protoTokenId)
                                .setAccountId(protoOldTreasury)))
                .build();
        recordItems.add(tokenCreateRecordItem);

        // when
        // mint
        var mintSerials = List.of(1L, 2L, 3L);
        var metadata = recordItemBuilder.bytes(16);
        var mintTransfers = mintSerials.stream()
                .map(serial -> NftTransfer.newBuilder()
                        .setSerialNumber(serial)
                        .setReceiverAccountID(protoOldTreasury)
                        .build())
                .toList();
        var nftMintTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addAllNftTransfers(mintTransfers)
                .build();
        var nftMintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(
                        b -> b.setToken(protoTokenId).clearMetadata().addAllMetadata(Collections.nCopies(3, metadata)))
                .record(r -> r.addTokenTransferLists(nftMintTransferList))
                .receipt(r -> r.clearSerialNumbers().addAllSerialNumbers(mintSerials))
                .build();
        recordItems.add(nftMintRecordItem);

        // transfer serial number 2 to account
        var nftTransfer = NftTransfer.newBuilder()
                .setSerialNumber(2L)
                .setReceiverAccountID(protoAccount)
                .setSenderAccountID(protoOldTreasury)
                .build();
        var nftTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addNftTransfers(nftTransfer)
                .build();
        var nftTransferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .record(r -> r.addTokenTransferLists(nftTransferList))
                .build();
        recordItems.add(nftTransferRecordItem);

        long newTreasuryAssociationTimestamp = 0;
        if (explicitAssociation) {
            var recordItem = recordItemBuilder
                    .tokenAssociate()
                    .transactionBody(
                            b -> b.setAccount(protoNewTreasury).clearTokens().addTokens(protoTokenId))
                    .build();
            newTreasuryAssociationTimestamp = recordItem.getConsensusTimestamp();
            recordItems.add(recordItem);
        }

        // token update which changes treasury
        var nftTreasuryUpdate = NftTransfer.newBuilder()
                .setSerialNumber(WILDCARD_SERIAL_NUMBER)
                .setReceiverAccountID(protoNewTreasury)
                .setSenderAccountID(protoOldTreasury)
                .build();
        var nftTreasuryUpdateTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addNftTransfers(nftTreasuryUpdate)
                .build();
        var nftUpdateRecordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.setToken(protoTokenId).setTreasury(protoNewTreasury))
                .record(r -> {
                    r.addTokenTransferLists(nftTreasuryUpdateTransferList);
                    if (!explicitAssociation) {
                        r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(protoNewTreasury)
                                .setTokenId(protoTokenId));
                    }
                })
                .build();
        if (!explicitAssociation) {
            newTreasuryAssociationTimestamp = nftUpdateRecordItem.getConsensusTimestamp();
        }
        recordItems.add(nftUpdateRecordItem);

        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        var metadataBytes = DomainUtils.toBytes(metadata);
        long mintTimestamp = nftMintRecordItem.getConsensusTimestamp();
        long transferTimestamp = nftTransferRecordItem.getConsensusTimestamp();
        long updateTimestamp = nftUpdateRecordItem.getConsensusTimestamp();
        var nft1 = Nft.builder()
                .accountId(newTreasury)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(1)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft2 = Nft.builder()
                .accountId(account)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(2)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft3 = Nft.builder()
                .accountId(newTreasury)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(3)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft1History = nft1.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        var nft2History = nft2.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, transferTimestamp))
                .build();
        var nft3History = nft3.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1History, nft2History, nft3History);

        assertNftTransferInRepository(
                nftMintRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 1, protoTokenId),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 2, protoTokenId),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 3, protoTokenId));
        assertNftTransferInRepository(
                nftTransferRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoAccount, protoOldTreasury, 2, protoTokenId));
        assertNftTransferInRepository(
                nftUpdateRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoNewTreasury, protoOldTreasury, WILDCARD_SERIAL_NUMBER, protoTokenId));

        var tokenAccountOldTreasury = TokenAccount.builder()
                .accountId(oldTreasury.getId())
                .associated(true)
                .automaticAssociation(false)
                .balance(0L)
                .createdTimestamp(tokenCreateRecordItem.getConsensusTimestamp())
                .timestampRange(Range.atLeast(tokenCreateRecordItem.getConsensusTimestamp()))
                .tokenId(tokenId.getId())
                .build();
        var tokenAccountNewTreasury = TokenAccount.builder()
                .accountId(newTreasury.getId())
                .associated(true)
                .automaticAssociation(!explicitAssociation)
                .balance(2L)
                .createdTimestamp(newTreasuryAssociationTimestamp)
                .timestampRange(Range.atLeast(newTreasuryAssociationTimestamp))
                .tokenId(tokenId.getId())
                .build();
        assertThat(tokenAccountRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("freezeStatus", "kycStatus")
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);
    }

    @Test
    void tokenAccountFreeze() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                true,
                false,
                false,
                INITIAL_SUPPLY);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(freezeTimeStamp, transaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE,
                freezeTimeStamp);
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

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE,
                unfreezeTimeStamp);
    }

    @Test
    void tokenAccountGrantKyc() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                true,
                false,
                INITIAL_SUPPLY);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, transaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.GRANTED,
                grantTimeStamp);
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

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                0,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.REVOKED,
                revokeTimestamp);
    }

    @Test
    void tokenBurn() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, false, amount, null);

        // when
        insertAndParseTransaction(burnTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // then
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, burnTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, INITIAL_SUPPLY - amount);

        assertThat(contractLogRepository.findById(new ContractLog.Id(burnTimestamp, 0)))
                .get()
                .returns(burnTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(PAYER2.getAccountNum()).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(-amount).toArray(), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(burnTimestamp))
                .hasSize(1)
                .first()
                .returns(burnTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 12L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .spender(EntityId.of(SPENDER))
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).get().isEqualTo(expectedNft1);

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L);
            builder.addTokenTransferLists(burnTransfer);
        });

        expectedNft1.setAccountId(null);
        expectedNft1.setDeleted(true);
        expectedNft1.setTimestampLower(burnTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        // Verify
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                burnTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER, SERIAL_NUMBER_1, TOKEN_ID));

        assertThat(tokenTransferRepository.findAll()).isEmpty();
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 1);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);

        assertThat(contractLogRepository.findById(new ContractLog.Id(burnTimestamp, 0)))
                .get()
                .returns(burnTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(PAYER.getAccountNum()).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(SERIAL_NUMBER_1).toArray(), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(burnTimestamp))
                .hasSize(1)
                .first()
                .returns(burnTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNftMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer);
        });

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1);
            builder.addTokenTransferLists(burnTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                burnTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER, SERIAL_NUMBER_1, TOKEN_ID));

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, burnTimestamp, SYMBOL, 1);
        var nft1 = Nft.builder()
                .deleted(true)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(burnTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void tokenMint() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, true, amount, null);
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY + amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);

        assertThat(contractLogRepository.findById(new ContractLog.Id(mintTimestamp, 0)))
                .get()
                .returns(mintTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(PAYER2.getAccountNum()).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(amount).toArray(), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(mintTimestamp))
                .hasSize(1)
                .first()
                .returns(mintTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnFtsPrecompile() {
        tokenSupplyFtsPrecompile(false);
    }

    @Test
    void tokenMintFtsPrecompile() {
        tokenSupplyFtsPrecompile(true);
    }

    private void tokenSupplyFtsPrecompile(boolean isMint) {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, isMint, amount, null);
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY + amount);
            builder.addTokenTransferLists(tokenTransfer);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, INITIAL_SUPPLY + amount);

        assertContractResult(mintTimestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNfts() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 2);

        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        assertThat(contractLogRepository.findById(new ContractLog.Id(mintTimestamp, 0)))
                .get()
                .returns(mintTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(PAYER.getAccountNum()).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(SERIAL_NUMBER_1).toArray(), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(mintTimestamp))
                .hasSize(1)
                .first()
                .returns(mintTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNftsPrecompile() {
        // given
        long mintTimestamp = 10L;
        tokenSupplyMintNftsPrecompile(mintTimestamp);

        long burnTimestamp = 15L;
        var transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, SERIAL_NUMBER_LIST);
        var contractFunctionResultAtomic = new AtomicReference<ContractFunctionResult>();

        // when
        insertAndParseTransaction(burnTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // then
        var nft1 = Nft.builder()
                .createdTimestamp(mintTimestamp)
                .deleted(true)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(burnTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        var nftHistory = Stream.of(nft1, nft2)
                .map(Nft::toBuilder)
                .map(b -> b.accountId(PAYER_ACCOUNT_ID)
                        .deleted(false)
                        .timestampRange(Range.closedOpen(mintTimestamp, burnTimestamp))
                        .build())
                .toList();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        assertContractResult(burnTimestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNftsPrecompile() {
        tokenSupplyMintNftsPrecompile(10L);
    }

    private void tokenSupplyMintNftsPrecompile(long timestamp) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        // when
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(timestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // then
        assertNftTransferInRepository(
                timestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, timestamp, SYMBOL, 2);

        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(timestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        assertContractResult(timestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNftsMissingToken() {
        // given
        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 2, SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);
        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithoutAutoTokenAssociations(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, false, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferPrecompile(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, false, true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithAutoTokenAssociations(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, true, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintAllowanceTransfer(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        // mint
        long mintTimestamp = CREATE_TIMESTAMP + 20;
        var metadata = recordItemBuilder.bytes(16);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();

        // approve allowance
        var approveAllowanceTimestamp = mintTimestamp + 20;
        var approveAllowanceRecordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> b.clear()
                        .addNftAllowances(NftAllowance.newBuilder()
                                .addSerialNumbers(1)
                                .setSpender(PAYER2)
                                .setTokenId(TOKEN_ID)))
                .transactionBodyWrapper(w -> w.setTransactionID(Utility.getTransactionId(PAYER)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(approveAllowanceTimestamp)))
                .build();

        // transfer using allowance, PAYER2 transfers nft serial 1 from PAYER to RECEIVER
        var transferTimestamp = approveAllowanceTimestamp + 20;
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setIsApproval(true)
                                        .setReceiverAccountID(RECEIVER)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(1))))
                .transactionBodyWrapper(w -> w.setTransactionID(Utility.getTransactionId(PAYER2)))
                .record(r -> r.clearTokenTransferLists()
                        .setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(RECEIVER)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();
        var recordItems = List.of(mintRecordItem, approveAllowanceRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID));
        assertNftTransferInRepository(transferTimestamp, domainNftTransfer(RECEIVER, PAYER, 1, TOKEN_ID, true));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);

        var nft = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        // with spender
        var nftHistory1 = nft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .spender(EntityId.of(PAYER2))
                .timestampRange(Range.closedOpen(approveAllowanceTimestamp, transferTimestamp))
                .build();
        // when mint
        var nftHistory2 = nft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp, approveAllowanceTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(nft);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nftHistory1, nftHistory2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintTransferDuplicateTransfersInRecord(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        long mintTimestamp = 20;
        var metadata1 = recordItemBuilder.bytes(16);
        var metadata2 = recordItemBuilder.bytes(16);
        var mintNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER)
                        .setSerialNumber(2)
                        .build());
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(
                        b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata1).addMetadata(metadata2))
                .receipt(r -> r.clearSerialNumbers()
                        .addSerialNumbers(1)
                        .addSerialNumbers(2)
                        .setNewTotalSupply(2))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .addTokenTransferLists(
                                TokenTransferList.newBuilder()
                                        .setToken(TOKEN_ID)
                                        .addAllNftTransfers(mintNftTransfers)
                                        .addAllNftTransfers(mintNftTransfers) // duplicate
                                ))
                .build();

        long transferTimestamp = mintTimestamp + 10;
        var transferNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(2)
                        .build());
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(
                                TokenTransferList.newBuilder()
                                        .setToken(TOKEN_ID)
                                        .addAllNftTransfers(transferNftTransfers)
                                        .addAllNftTransfers(transferNftTransfers) // duplicate
                                ))
                .build();

        var recordItems = List.of(mintRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                // with duplicates
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 2, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                // with duplicates
                domainNftTransfer(PAYER2, PAYER, 1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 2, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 2, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 2);

        var nft1 = Nft.builder()
                .accountId(EntityId.of(PAYER2))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata1))
                .serialNumber(1)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .timestampRange(Range.atLeast(transferTimestamp))
                .build();
        var nft2 = nft1.toBuilder()
                .metadata(DomainUtils.toBytes(metadata2))
                .serialNumber(2)
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);

        List.of(nft1, nft2).forEach(n -> {
            n.setAccountId(PAYER_ACCOUNT_ID);
            n.setTimestampRange(Range.closedOpen(mintTimestamp, transferTimestamp));
        });
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1, nft2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintTransferMultipleParties(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        long mintTimestamp = 20;
        var metadata = recordItemBuilder.bytes(16);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();

        long transferTimestamp = mintTimestamp + 10;
        // same NFT transferred twice, PAYER -> PAYER2 -> RECEIVER
        var transferNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER2)
                        .setSerialNumber(1)
                        .build());
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .build();

        var recordItems = List.of(mintRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER2, SERIAL_NUMBER_1, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, mintTimestamp, SYMBOL, 1);

        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .timestampRange(Range.atLeast(transferTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(nft1);

        nft1.setAccountId(PAYER_ACCOUNT_ID);
        nft1.setTimestampRange(Range.closedOpen(mintTimestamp, transferTimestamp));
        assertThat(findHistory(Nft.class)).containsExactly(nft1);
    }

    @Test
    void nftTransfer() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);
        long mintTimestamp1 = 20L;
        var mintTransfer1 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_1));
        var mintTransaction1 = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_1));

        insertAndParseTransaction(mintTimestamp1, mintTransaction1, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addSerialNumbers(SERIAL_NUMBER_1);
            builder.addTokenTransferLists(mintTransfer1);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 25L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp1)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .spender(EntityId.of(SPENDER))
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).get().isEqualTo(expectedNft1);

        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction2 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        // Verify
        insertAndParseTransaction(mintTimestamp2, mintTransaction2, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer2);
        });

        // token transfer
        var expectedEntityTransactions =
                Streams.stream(entityTransactionRepository.findAll()).collect(Collectors.toList());
        var transaction = tokenTransferTransaction();
        var transferList = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2)
                        .build())
                .build();
        long transferTimestamp = 40L;
        var recordItem = insertAndParseTransaction(
                transferTimestamp, transaction, builder -> builder.addTokenTransferLists(transferList));

        // then
        expectedNft1.setAccountId(EntityId.of(RECEIVER));
        expectedNft1.setTimestampLower(transferTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp2)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        assertNftTransferInRepository(
                mintTimestamp1, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                mintTimestamp2, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);
        assertThat(contractLogRepository.findById(new ContractLog.Id(transferTimestamp, 0)))
                .get()
                .returns(transferTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(PAYER.getAccountNum()).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(RECEIVER.getAccountNum()).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(SERIAL_NUMBER_1).toArray(), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(transferTimestamp))
                .hasSize(1)
                .first()
                .returns(transferTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));

        var entityIds = Lists.newArrayList(
                EntityId.of(recordItem.getTransactionBody().getNodeAccountID()),
                recordItem.getPayerAccountId(),
                EntityId.of(PAYER),
                EntityId.of(RECEIVER),
                EntityId.of(TOKEN_ID));
        expectedEntityTransactions.addAll(toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values());
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            true, true
                            true, false
                            false, true
                            false, false
                            """)
    void nftTransfersHaveCorrectIsApprovalValue(boolean isApproval1, boolean isApproval2) {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        // mint transfer / transaction 1
        long mintTimestamp1 = 20L;
        TokenTransferList mintTransfer1 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_1));
        Transaction mintTransaction1 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_1));

        insertAndParseTransaction(mintTimestamp1, mintTransaction1, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addSerialNumbers(SERIAL_NUMBER_1);
            builder.addTokenTransferLists(mintTransfer1);
        });

        // mint transfer / transaction 2
        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction2 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTimestamp2, mintTransaction2, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer2);
        });

        // token transfer
        Transaction transaction = buildTransaction(builder -> {
            // NFT transfer list 1
            TokenTransferList transferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_1)
                            .setIsApproval(isApproval1)
                            .build())
                    .build();

            // NFT transfer list 2
            TokenTransferList transferList2 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_2)
                            .setIsApproval(isApproval2)
                            .build())
                    .build();

            builder.getCryptoTransferBuilder().addTokenTransfers(transferList1).addTokenTransfers(transferList2);
        });

        long transferTimestamp = 40L;
        insertAndParseTransaction(transferTimestamp, transaction, builder -> {
            // NFT transfer list 1
            TokenTransferList transferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_1)
                            .build())
                    .build();

            // NFT transfer list 2
            TokenTransferList transferList2 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_2)
                            .build())
                    .build();

            builder.addAllTokenTransferLists(List.of(transferList1, transferList2));
        });

        assertNftTransferInRepository(
                mintTimestamp1, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                mintTimestamp2, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID, isApproval1),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID, isApproval2));

        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp1)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder()
                .createdTimestamp(mintTimestamp2)
                .serialNumber(SERIAL_NUMBER_2)
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);

        var nft1History = nft1.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp1, transferTimestamp))
                .build();
        var nft2History = nft2.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp2, transferTimestamp))
                .build();
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1History, nft2History);
    }

    @Test
    void nftTransferMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        // token transfer
        var transaction = tokenTransferTransaction();
        var transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .build();
        var transferList2 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2)
                        .build())
                .build();

        long transferTimestamp = 25L;
        insertAndParseTransaction(
                transferTimestamp,
                transaction,
                builder -> builder.addAllTokenTransferLists(List.of(transferList1, transferList2)));

        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .deleted(false)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void tokenTransfersMustHaveCorrectIsApprovalValue() {
        entityProperties.getPersist().setTrackAllowance(true);
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        // approve allowance for fungible token
        var allowanceAmount = INITIAL_SUPPLY / 4L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addTokenAllowances(TokenAllowance.newBuilder()
                        .setTokenId(TOKEN_ID)
                        .setOwner(PAYER2)
                        .setSpender(PAYER)
                        .setAmount(allowanceAmount)));
        insertAndParseTransaction(ALLOWANCE_TIMESTAMP, cryptoApproveAllowanceTransaction);

        TokenID tokenId2 = TokenID.newBuilder().setTokenNum(7).build();
        createTokenEntity(tokenId2, FUNGIBLE_COMMON, "MIRROR", 10L, false, false, false);

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();
        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-1000)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER2)
                        .setAmount(-100)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(1000)
                        .build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenId2)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(333)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(-333)
                        .build())
                .build();
        List<TokenTransferList> transferLists = List.of(transferList1, transferList2);

        // when
        Transaction transaction = buildTransaction(builder -> {
            TokenTransferList bodyTransferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER)
                            .setAmount(-600)
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER2)
                            .setAmount(-100) // Decrement from allowance amount
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER2)
                            .setAmount(-900) // Decrement from allowance amount
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(accountId)
                            .setAmount(-333)
                            .build())
                    .build();
            builder.getCryptoTransferBuilder().addTokenTransfers(bodyTransferList1);
        });
        insertAndParseTransaction(TRANSFER_TIMESTAMP, transaction, builder -> {
            builder.addAllTokenTransferLists(transferLists);
        });

        // then
        assertTokenTransferInRepository(TOKEN_ID, PAYER, TRANSFER_TIMESTAMP, -1000, true);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, TRANSFER_TIMESTAMP, -100, true);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenTransferInRepository(tokenId2, PAYER, TRANSFER_TIMESTAMP, 333);
        assertTokenTransferInRepository(tokenId2, accountId, TRANSFER_TIMESTAMP, -333);
        assertTokenAllowanceInRepository(
                TOKEN_ID, PAYER2, PAYER, allowanceAmount, allowanceAmount - 1000L, ALLOWANCE_TIMESTAMP);
    }

    @Test
    void tokenWipe() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, transferAmount));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount, Collections.emptyList());
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - wipeAmount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, INITIAL_SUPPLY - wipeAmount);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, wipeTimestamp, transferAmount);

        assertThat(contractLogRepository.findById(new ContractLog.Id(wipeTimestamp, 0)))
                .get()
                .returns(wipeTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(PAYER2.getAccountNum()).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(-transferAmount).toArray(), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(wipeTimestamp))
                .hasSize(1)
                .first()
                .returns(wipeTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenWipeNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 12L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .spender(EntityId.of(SPENDER))
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).get().isEqualTo(expectedNft1);

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER2, List.of(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L);
            builder.addTokenTransferLists(wipeTransfer);
        });
        expectedNft1.setAccountId(null);
        expectedNft1.setDeleted(true);
        expectedNft1.setTimestampLower(wipeTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        // Verify
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                wipeTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER2, SERIAL_NUMBER_1, TOKEN_ID));

        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 1);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);

        assertThat(contractLogRepository.findById(new ContractLog.Id(wipeTimestamp, 0)))
                .get()
                .returns(wipeTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(Bytes.ofUnsignedLong(PAYER2.getAccountNum()).toArray(), from(ContractLog::getTopic1))
                .returns(Bytes.ofUnsignedLong(0).toArray(), from(ContractLog::getTopic2))
                .returns(Bytes.ofUnsignedLong(SERIAL_NUMBER_1).toArray(), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(wipeTimestamp))
                .hasSize(1)
                .first()
                .returns(wipeTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenWipeWithMissingToken() {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, 100L, null);
        insertAndParseTransaction(10L, transaction);

        assertTokenInRepository(TOKEN_ID, false, CREATE_TIMESTAMP, ASSOCIATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY);
    }

    @Test
    void tokenWipeNftMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                0);

        long wipeTimestamp = 15L;
        var wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, RECEIVER, List.of(SERIAL_NUMBER_1));
        var transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.addTokenTransferLists(wipeTransfer);
        });

        // Verify
        assertNftTransferInRepository(
                wipeTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, RECEIVER, SERIAL_NUMBER_1, TOKEN_ID));
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, 0);

        var expected = Nft.builder()
                .deleted(true)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(wipeTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findAll()).containsExactly(expected);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void tokenCreateAndAssociateAndWipeInSameRecordFile() {
        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        long newTotalSupply = INITIAL_SUPPLY - wipeAmount;

        // create token with a transfer
        Transaction createTransaction = tokenCreateTransaction(FUNGIBLE_COMMON, false, false, false, SYMBOL);
        TokenTransferList createTokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, INITIAL_SUPPLY));
        RecordItem createTokenRecordItem = getRecordItem(CREATE_TIMESTAMP, createTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY).setTokenID(TOKEN_ID);
            builder.addTokenTransferLists(createTokenTransfer);
        });

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        RecordItem associateRecordItem = getRecordItem(ASSOCIATE_TIMESTAMP, associateTransaction);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, transferAmount));
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount, null);
        RecordItem wipeRecordItem = getRecordItem(wipeTimestamp, wipeTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(newTotalSupply);
            builder.addTokenTransferLists(wipeTokenTransfer);
        });

        // process all record items in a single file
        parseRecordItemsAndCommit(List.of(createTokenRecordItem, associateRecordItem, wipeRecordItem));

        // Verify token, tokenAccount and tokenTransfer
        assertTokenInRepository(TOKEN_ID, true, CREATE_TIMESTAMP, wipeTimestamp, SYMBOL, newTotalSupply);
        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                999000L,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                ASSOCIATE_TIMESTAMP);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, wipeTimestamp, transferAmount);
    }

    void tokenCreate(
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            List<TokenAccount> expectedTokenAccounts,
            List<EntityId> autoAssociatedAccounts) {
        // given
        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN_REF_KEY,
                PAYER.getAccountNum(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                null,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);

        // when
        createTokenEntity(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                customFees,
                autoAssociatedAccounts);

        // then
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        // verify token
        TokenPauseStatusEnum pauseStatus =
                pauseKey ? TokenPauseStatusEnum.UNPAUSED : TokenPauseStatusEnum.NOT_APPLICABLE;
        assertTokenInRepository(
                TOKEN_ID, true, CREATE_TIMESTAMP, CREATE_TIMESTAMP, SYMBOL, INITIAL_SUPPLY, pauseStatus);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertCustomFeesInDb(customFees);
        assertThat(tokenTransferRepository.count()).isEqualTo(1L);
    }

    void tokenTransfer(
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees,
            boolean hasAutoTokenAssociations,
            boolean isPrecompile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                INITIAL_SUPPLY);
        TokenID tokenId2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenId2, FUNGIBLE_COMMON, symbol2, 10L, false, false, false);

        var existingEntityTransactions = Streams.stream(entityTransactionRepository.findAll())
                .collect(Collectors.toMap(EntityTransaction::getId, Function.identity()));

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-1000)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(1000)
                        .build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenId2)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(333)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(-333)
                        .build())
                .build();
        List<TokenTransferList> transferLists = List.of(transferList1, transferList2);

        // token treasury associations <TOKEN_ID, PAYER> and <tokenId2, PAYER> are created in the token create
        // transaction and they are not auto associations; the two token transfers' <token, recipient> pairs are
        // <TOKEN_ID, accountId> and <tokenId2, PAYER>, since <tokenId2, PAYER> already exists, only
        // <TOKEN_ID accountId> will be auto associated
        var autoTokenAssociation = TokenAssociation.newBuilder()
                .setAccountId(accountId)
                .setTokenId(TOKEN_ID)
                .build();

        var autoTokenAccount = TokenAccount.builder()
                .accountId(EntityId.of(accountId).getId())
                .associated(true)
                .automaticAssociation(true)
                .balance(1000L)
                .createdTimestamp(TRANSFER_TIMESTAMP)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .timestampRange(Range.atLeast(TRANSFER_TIMESTAMP))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        List<TokenAccount> expectedAutoAssociatedTokenAccounts =
                hasAutoTokenAssociations ? List.of(autoTokenAccount) : Collections.emptyList();

        // when
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        var recordItem = insertAndParseTransaction(TRANSFER_TIMESTAMP, transaction, builder -> {
            builder.addAllTokenTransferLists(transferLists).addAllAssessedCustomFees(protoAssessedCustomFees);
            if (hasAutoTokenAssociations) {
                builder.addAutomaticTokenAssociations(autoTokenAssociation);
            }
            if (isPrecompile) {
                buildContractFunctionResult(builder.getContractCallResultBuilder());
                contractFunctionResultAtomic.set(builder.getContractCallResult());
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

        if (isPrecompile) {
            assertContractResult(TRANSFER_TIMESTAMP, contractFunctionResultAtomic.get());
        }

        var entityIds = Lists.newArrayList(
                recordItem.getPayerAccountId(),
                EntityId.of(recordItem.getTransactionBody().getNodeAccountID()),
                EntityId.of(accountId),
                EntityId.of(PAYER),
                EntityId.of(TOKEN_ID),
                EntityId.of(tokenId2));
        assessedCustomFees.forEach(assessedCustomFee -> {
            entityIds.add(assessedCustomFee.getId().getCollectorAccountId());
            entityIds.add(assessedCustomFee.getTokenId());
            assessedCustomFee.getEffectivePayerAccountIds().forEach(id -> entityIds.add(EntityId.of(id, ACCOUNT)));
        });
        if (isPrecompile) {
            entityIds.add(EntityId.of(CONTRACT_ID));
            entityIds.add(EntityId.of(CREATED_CONTRACT_ID));
        }
        var expectedEntityTransactions = new HashMap<>(existingEntityTransactions);
        toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values()
                .forEach(e -> expectedEntityTransactions.put(e.getId(), e));

        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions.values());
    }

    private RecordItem getRecordItem(long consensusTimestamp, Transaction transaction) {
        return getRecordItem(consensusTimestamp, transaction, builder -> {});
    }

    private RecordItem getRecordItem(
            long consensusTimestamp, Transaction transaction, Consumer<TransactionRecord.Builder> customBuilder) {
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                builder -> {
                    builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));
                    customBuilder.accept(builder);
                },
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        return RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
    }

    private RecordItem insertAndParseTransaction(long consensusTimestamp, Transaction transaction) {
        return insertAndParseTransaction(consensusTimestamp, transaction, builder -> {});
    }

    private RecordItem insertAndParseTransaction(
            long consensusTimestamp, Transaction transaction, Consumer<TransactionRecord.Builder> customBuilder) {
        var recordItem = getRecordItem(consensusTimestamp, transaction, customBuilder);
        parseRecordItemAndCommit(recordItem);
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, consensusTimestamp, null);
        return recordItem;
    }

    private com.hedera.mirror.common.domain.token.NftTransfer domainNftTransfer(
            AccountID receiver, AccountID sender, long serialNumber, TokenID tokenId) {
        return domainNftTransfer(receiver, sender, serialNumber, tokenId, false);
    }

    private com.hedera.mirror.common.domain.token.NftTransfer domainNftTransfer(
            AccountID receiver, AccountID sender, long serialNumber, TokenID tokenId, boolean isApproval) {
        var receiverEntityId = receiver.equals(DEFAULT_ACCOUNT_ID) ? null : EntityId.of(receiver);
        var senderEntityId = sender.equals(DEFAULT_ACCOUNT_ID) ? null : EntityId.of(sender);

        return com.hedera.mirror.common.domain.token.NftTransfer.builder()
                .isApproval(isApproval)
                .receiverAccountId(receiverEntityId)
                .senderAccountId(senderEntityId)
                .serialNumber(serialNumber)
                .tokenId(EntityId.of(tokenId))
                .build();
    }

    private Transaction tokenCreateTransaction(
            TokenType tokenType,
            boolean freezeDefault,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            String symbol,
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

    private Transaction tokenCreateTransaction(
            TokenType tokenType, boolean setFreezeKey, boolean setKycKey, boolean setPauseKey, String symbol) {
        return tokenCreateTransaction(
                tokenType, false, setFreezeKey, setKycKey, setPauseKey, symbol, Collections.emptyList());
    }

    private Transaction tokenUpdateTransaction(
            TokenID tokenID, String symbol, String memo, Key newKey, AccountID autoRenewAccount, AccountID treasury) {
        return buildTransaction(builder -> {
            builder.getTokenUpdateBuilder()
                    .setAdminKey(newKey)
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
                    .setTreasury(treasury)
                    .setWipeKey(newKey);
            if (autoRenewAccount != null) {
                builder.getTokenUpdateBuilder().setAutoRenewAccount(autoRenewAccount);
            }
        });
    }

    private Transaction tokenAssociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder ->
                builder.getTokenAssociateBuilder().setAccount(accountID).addAllTokens(tokenIDs));
    }

    private Transaction tokenDissociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder ->
                builder.getTokenDissociateBuilder().setAccount(accountID).addAllTokens(tokenIDs));
    }

    private Transaction tokenDeleteTransaction(TokenID tokenID) {
        return buildTransaction(builder -> builder.getTokenDeletionBuilder().setToken(tokenID));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, boolean freeze) {
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(
                    builder -> builder.getTokenFreezeBuilder().setToken(tokenID).setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder ->
                    builder.getTokenUnfreezeBuilder().setToken(tokenID).setAccount(PAYER2));
        }

        return transaction;
    }

    private Transaction tokenPauseTransaction(TokenID tokenID, boolean pause) {
        Transaction transaction;
        if (pause) {
            transaction =
                    buildTransaction(builder -> builder.getTokenPauseBuilder().setToken(tokenID));
        } else {
            transaction =
                    buildTransaction(builder -> builder.getTokenUnpauseBuilder().setToken(tokenID));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenID, boolean kyc) {
        Transaction transaction;
        if (kyc) {
            transaction = buildTransaction(builder ->
                    builder.getTokenGrantKycBuilder().setToken(tokenID).setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder ->
                    builder.getTokenRevokeKycBuilder().setToken(tokenID).setAccount(PAYER2));
        }

        return transaction;
    }

    private Transaction tokenSupplyTransaction(
            TokenID tokenID, TokenType tokenType, boolean mint, long amount, List<Long> serialNumbers) {
        Transaction transaction;
        if (mint) {
            transaction = buildTransaction(builder -> {
                builder.getTokenMintBuilder().setToken(tokenID);

                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenMintBuilder().setAmount(amount);
                } else {
                    builder.getTokenMintBuilder()
                            .addAllMetadata(Collections.nCopies(serialNumbers.size(), DomainUtils.fromBytes(METADATA)));
                }
            });
        } else {
            transaction = buildTransaction(builder -> {
                builder.getTokenBurnBuilder().setToken(tokenID);
                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenBurnBuilder().setAmount(amount);
                } else {
                    builder.getTokenBurnBuilder().addAllSerialNumbers(serialNumbers);
                }
            });
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(
            TokenID tokenID, TokenType tokenType, long amount, List<Long> serialNumbers) {
        return buildTransaction(builder -> {
            builder.getTokenWipeBuilder().setToken(tokenID).setAccount(PAYER).build();
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

    private void assertTokenInRepository(
            TokenID tokenID,
            boolean present,
            long createdTimestamp,
            long modifiedTimestamp,
            String symbol,
            long totalSupply,
            byte[] keyData,
            TokenPauseStatusEnum pauseStatus,
            String... keyFields) {
        Optional<Token> tokenOptional =
                tokenRepository.findById(EntityId.of(tokenID).getId());
        if (present) {
            assertThat(tokenOptional)
                    .get()
                    .returns(createdTimestamp, from(Token::getCreatedTimestamp))
                    .returns(modifiedTimestamp, from(Token::getTimestampLower))
                    .returns(symbol, from(Token::getSymbol))
                    .returns(pauseStatus, from(Token::getPauseStatus))
                    .returns(totalSupply, from(Token::getTotalSupply));
            if (keyFields.length != 0) {
                assertThat(tokenOptional)
                        .get()
                        .extracting(keyFields)
                        .containsExactlyElementsOf(
                                Arrays.stream(keyFields).map((v) -> keyData).collect(Collectors.toList()));
            }
        } else {
            assertThat(tokenOptional).isNotPresent();
        }
    }

    private void assertTokenInRepository(
            TokenID tokenID,
            boolean present,
            long createdTimestamp,
            long modifiedTimestamp,
            String symbol,
            long totalSupply) {
        assertTokenInRepository(
                tokenID,
                present,
                createdTimestamp,
                modifiedTimestamp,
                symbol,
                totalSupply,
                null,
                TokenPauseStatusEnum.NOT_APPLICABLE);
    }

    private void assertTokenInRepository(
            TokenID tokenID,
            boolean present,
            long createdTimestamp,
            long modifiedTimestamp,
            String symbol,
            long totalSupply,
            TokenPauseStatusEnum pauseStatus) {
        assertTokenInRepository(
                tokenID, present, createdTimestamp, modifiedTimestamp, symbol, totalSupply, null, pauseStatus);
    }

    private void assertNftTransferInRepository(
            long consensusTimestamp, com.hedera.mirror.common.domain.token.NftTransfer... nftTransfers) {
        assertThat(transactionRepository.findById(consensusTimestamp))
                .get()
                .extracting(com.hedera.mirror.common.domain.transaction.Transaction::getNftTransfer)
                .asList()
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(nftTransfers));
    }

    private void assertTokenAccountInRepository(
            TokenID tokenID,
            AccountID accountId,
            long balance,
            long createdTimestamp,
            boolean associated,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            long timestampLowerBound) {
        var expected = TokenAccount.builder()
                .accountId(EntityId.of(accountId).getId())
                .associated(associated)
                .automaticAssociation(false)
                .balance(balance)
                .createdTimestamp(createdTimestamp)
                .freezeStatus(freezeStatus)
                .kycStatus(kycStatus)
                .timestampRange(Range.atLeast(timestampLowerBound))
                .tokenId(EntityId.of(tokenID).getId())
                .build();

        assertThat(tokenAccountRepository.findById(expected.getId())).get().isEqualTo(expected);
    }

    private void assertTokenAccountNotInRepository(TokenID tokenId, AccountID accountId) {
        var id = new AbstractTokenAccount.Id();
        id.setAccountId(EntityId.of(accountId).getId());
        id.setTokenId(EntityId.of(tokenId).getId());
        assertThat(tokenAccountRepository.findById(id)).isNotPresent();
    }

    private void assertTokenTransferInRepository(
            TokenID tokenID, AccountID accountID, long consensusTimestamp, long amount) {
        assertTokenTransferInRepository(tokenID, accountID, consensusTimestamp, amount, false);
    }

    private void assertTokenTransferInRepository(
            TokenID tokenID, AccountID accountID, long consensusTimestamp, long amount, boolean isApproval) {
        var expected = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(amount)
                        .id(new TokenTransfer.Id(consensusTimestamp, EntityId.of(tokenID), EntityId.of(accountID)))
                        .isApproval(isApproval)
                        .payerAccountId(PAYER_ACCOUNT_ID)
                        .deletedTokenDissociate(false))
                .get();
        assertThat(tokenTransferRepository.findById(expected.getId())).get().isEqualTo(expected);
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

    private void assertContractResult(long timestamp, ContractFunctionResult contractFunctionResult) {
        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(timestamp))
                .hasSize(1)
                .first()
                .returns(EntityId.of(CONTRACT_ID).getId(), ContractResult::getContractId);

        contractResult
                .returns(contractFunctionResult.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(contractFunctionResult.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(timestamp, ContractResult::getConsensusTimestamp)
                .returns(contractFunctionResult.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(contractFunctionResult.getGasUsed(), ContractResult::getGasUsed);
    }

    private void assertTokenAllowanceInRepository(
            TokenID tokenId,
            AccountID owner,
            AccountID spender,
            long amountGranted,
            long amount,
            long allowanceTimestamp) {
        var expected = domainBuilder
                .tokenAllowance()
                .customize(a -> a.amountGranted(amountGranted)
                        .amount(amount)
                        .tokenId(EntityId.of(tokenId).getId())
                        .owner(EntityId.of(owner).getId())
                        .spender(EntityId.of(spender).getId())
                        .payerAccountId(EntityId.of(spender))
                        .timestampRange(Range.atLeast(allowanceTimestamp)))
                .get();
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(expected);
    }

    private void createTokenEntity(
            TokenID tokenId,
            TokenType tokenType,
            String symbol,
            long consensusTimestamp,
            boolean freezeDefault,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            List<CustomFee> customFees,
            List<EntityId> autoAssociatedAccounts) {
        var transaction = tokenCreateTransaction(
                tokenType, freezeDefault, setFreezeKey, setKycKey, setPauseKey, symbol, customFees);
        insertAndParseTransaction(consensusTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setTokenID(tokenId).setNewTotalSupply(INITIAL_SUPPLY);
            builder.addAllAutomaticTokenAssociations(autoAssociatedAccounts.stream()
                    .map(account -> TokenAssociation.newBuilder()
                            .setTokenId(tokenId)
                            .setAccountId(convertAccountId(account))
                            .build())
                    .collect(Collectors.toList()));
            if (tokenType == FUNGIBLE_COMMON) {
                builder.addTokenTransferLists(tokenTransferList(tokenId, accountAmount(PAYER, INITIAL_SUPPLY)));
            }
        });
    }

    private void createTokenEntity(
            TokenID tokenID,
            TokenType tokenType,
            String symbol,
            long consensusTimestamp,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey) {
        createTokenEntity(
                tokenID,
                tokenType,
                symbol,
                consensusTimestamp,
                false,
                setFreezeKey,
                setKycKey,
                setPauseKey,
                Collections.emptyList(),
                Collections.emptyList());
    }

    private void createAndAssociateToken(
            TokenID tokenID,
            TokenType tokenType,
            String symbol,
            long createTimestamp,
            long associateTimestamp,
            AccountID accountID,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            long initialSupply) {
        createTokenEntity(tokenID, tokenType, symbol, createTimestamp, setFreezeKey, setKycKey, setPauseKey);
        assertTokenInRepository(
                tokenID,
                true,
                createTimestamp,
                createTimestamp,
                symbol,
                initialSupply,
                setPauseKey ? TokenPauseStatusEnum.UNPAUSED : TokenPauseStatusEnum.NOT_APPLICABLE);

        Transaction associateTransaction = tokenAssociate(List.of(tokenID), accountID);
        insertAndParseTransaction(associateTimestamp, associateTransaction);

        assertTokenAccountInRepository(
                tokenID,
                accountID,
                0,
                associateTimestamp,
                true,
                setFreezeKey ? TokenFreezeStatusEnum.UNFROZEN : TokenFreezeStatusEnum.NOT_APPLICABLE,
                setKycKey ? TokenKycStatusEnum.REVOKED : TokenKycStatusEnum.NOT_APPLICABLE,
                associateTimestamp);
    }

    private void updateTokenFeeSchedule(TokenID tokenID, long consensusTimestamp, List<CustomFee> customFees) {
        Transaction transaction = buildTransaction(builder -> builder.getTokenFeeScheduleUpdateBuilder()
                .setTokenId(tokenID)
                .addAllCustomFees(convertCustomFees(customFees)));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                builder -> builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
    }

    private TokenTransferList tokenTransferList(TokenID tokenId, AccountAmount.Builder... accountAmounts) {
        var builder = TokenTransferList.newBuilder().setToken(tokenId);
        for (var aa : accountAmounts) {
            builder.addTransfers(aa);
        }
        return builder.build();
    }

    private TokenTransferList nftTransfer(
            TokenID tokenId, AccountID receiverAccountId, AccountID senderAccountId, List<Long> serialNumbers) {
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
            nftTransferBuilder.setIsApproval(false);
            builder.addNftTransfers(nftTransferBuilder);
        }
        return builder.build();
    }

    private com.hederahashgraph.api.proto.java.CustomFee convertCustomFee(CustomFee customFee) {
        var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder()
                .setFeeCollectorAccountId(convertAccountId(customFee.getCollectorAccountId()));

        if (customFee.getAmountDenominator() != null) {
            // fractional fee
            long maximumAmount = customFee.getMaximumAmount() != null ? customFee.getMaximumAmount() : 0;
            protoCustomFee.setFractionalFee(FractionalFee.newBuilder()
                    .setFractionalAmount(Fraction.newBuilder()
                            .setNumerator(customFee.getAmount())
                            .setDenominator(customFee.getAmountDenominator()))
                    .setMaximumAmount(maximumAmount)
                    .setMinimumAmount(customFee.getMinimumAmount())
                    .setNetOfTransfers(customFee.getNetOfTransfers()));
        } else if (customFee.getRoyaltyDenominator() != null) {
            // royalty fee
            RoyaltyFee.Builder royaltyFee = RoyaltyFee.newBuilder()
                    .setExchangeValueFraction(Fraction.newBuilder()
                            .setNumerator(customFee.getRoyaltyNumerator())
                            .setDenominator(customFee.getRoyaltyDenominator()));
            if (customFee.getAmount() != null) {
                royaltyFee.setFallbackFee(convertFixedFee(customFee));
            }

            protoCustomFee.setRoyaltyFee(royaltyFee);
        } else {
            // fixed fee
            protoCustomFee.setFixedFee(convertFixedFee(customFee));
        }

        protoCustomFee.setAllCollectorsAreExempt(customFee.isAllCollectorsAreExempt());

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

    @Builder
    static class TokenCreateArguments {
        List<EntityId> autoEnabledAccounts;
        List<Long> balances;
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
            String description = StringUtils.joinWith(
                    ", ",
                    customFeesDescription,
                    freezeDefault ? "freezeDefault false" : "freezeDefault true",
                    freezeKey ? "has freezeKey" : "no freezeKey",
                    kycKey ? "has kycKey" : "no kycKey");
            List<TokenAccount> tokenAccounts = autoEnabledAccounts.stream()
                    .map(account -> TokenAccount.builder()
                            .accountId(account.getId())
                            .associated(true)
                            .automaticAssociation(false)
                            .createdTimestamp(createdTimestamp)
                            .freezeStatus(freezeStatus)
                            .kycStatus(kycStatus)
                            .tokenId(tokenId.getId())
                            .timestampRange(Range.atLeast(createdTimestamp))
                            .build())
                    .collect(Collectors.toList());

            for (int i = 0; i < autoEnabledAccounts.size(); i++) {
                tokenAccounts.get(i).setBalance(balances.get(i));
            }

            return Arguments.of(description, customFees, freezeDefault, freezeKey, kycKey, pauseKey, tokenAccounts);
        }
    }
}
