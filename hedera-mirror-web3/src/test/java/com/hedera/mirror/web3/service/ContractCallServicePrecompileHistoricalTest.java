/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.FEE_SCHEDULE_KEY;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.FREEZE_KEY;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.KYC_KEY;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.PAUSE_KEY;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.SUPPLY_KEY;
import static com.hedera.mirror.web3.service.AbstractContractCallServiceTest.KeyType.WIPE_KEY;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ECDSA_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.KEY_WITH_ED_25519_TYPE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.LEDGER_ID;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SENDER_PUBLIC_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.utils.ExpiryFactory;
import com.hedera.mirror.web3.utils.HederaTokenFactory;
import com.hedera.mirror.web3.utils.KeyValueFactory;
import com.hedera.mirror.web3.utils.TokenKeyFactory;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.Expiry;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.FixedFee;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.FungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.HederaToken;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.KeyValue;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.NonFungibleTokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.TokenInfo;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContractHistorical.TokenKey;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.tx.Contract;

class ContractCallServicePrecompileHistoricalTest extends AbstractContractCallServiceTest {

    @BeforeAll
    static void setupFactories() {
        expiryFactory = new ExpiryFactory(PrecompileTestContractHistorical.class);
        keyValueFactory = new KeyValueFactory(PrecompileTestContractHistorical.class);
        tokenKeyFactory = new TokenKeyFactory(PrecompileTestContractHistorical.class);
        hederaTokenFactory = new HederaTokenFactory(PrecompileTestContractHistorical.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenFrozen(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var accountAndToken = persistAccountTokenAndFrozenRelationshipHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_isTokenFrozen(
                getAddressFromEntity(accountAndToken.getRight()), getAddressFromEntity(accountAndToken.getLeft()));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenFrozenWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var accountAndToken = persistAccountTokenAndFrozenRelationshipHistorical(historicalRange);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_isTokenFrozen(
                getAddressFromEntity(accountAndToken.getRight()), getAliasFromEntity(accountAndToken.getLeft()));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGranted(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        persistTokenRelationshipWithKycGrantedHistorical(tokenEntity, account, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        persistTokenRelationshipWithKycGrantedHistorical(tokenEntity, account, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedForNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = persistNftHistorical(historicalRange);
        persistTokenRelationshipWithKycGrantedHistorical(tokenEntity, account, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAddressFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isKycGrantedForNFTWithAlias(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var account = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistNftHistorical(historicalRange);
        persistTokenRelationshipWithKycGrantedHistorical(tokenEntity, account, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall =
                contract.call_isKycGranted(getAddressFromEntity(tokenEntity), getAliasFromEntity(account));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenAddress(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isTokenAddressNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistNftHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_isTokenAddress(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getDefaultKycToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getDefaultKycNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistNftHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultKyc(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenType(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getType(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ZERO);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenTypeNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistNftHistorical(historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getType(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.ONE);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenDefaultFreeze(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .freezeDefault(true)
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getNFTDefaultFreeze(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .freezeDefault(true)
                        .timestampRange(historicalRange))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getTokenDefaultFreeze(getAddressFromEntity(tokenEntity));

        // Then
        assertThat(functionCall.send()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithFixedFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        final var collectorAccount = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .amount(100L)
                .collectorAccountId(collectorAccount.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.fixedFees(List.of(fixedFee))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of())
                        .tokenId(tokenEntity.getId())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new PrecompileTestContractHistorical.FixedFee(
                BigInteger.valueOf(100L),
                getAddressFromEntity(tokenEntity),
                false,
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component1().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithFractionalFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var collectorAccount = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        final var fractionalFee = FractionalFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(10L)
                .minimumAmount(1L)
                .maximumAmount(1000L)
                .netOfTransfers(true)
                .numerator(100L)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.fractionalFees(List.of(fractionalFee))
                        .fixedFees(List.of())
                        .royaltyFees(List.of())
                        .tokenId(tokenEntity.getId())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new PrecompileTestContractHistorical.FractionalFee(
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1L),
                BigInteger.valueOf(1000L),
                true,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component2().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getCustomFeesForTokenWithRoyaltyFee(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var collectorAccount = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        final var royaltyFee = RoyaltyFee.builder()
                .collectorAccountId(collectorAccount.toEntityId())
                .denominator(10L)
                .fallbackFee(FallbackFee.builder()
                        .amount(100L)
                        .denominatingTokenId(tokenEntity.toEntityId())
                        .build())
                .numerator(20L)
                .build();
        domainBuilder
                .customFee()
                .customize(f -> f.royaltyFees(List.of(royaltyFee))
                        .fixedFees(List.of())
                        .fractionalFees(List.of())
                        .tokenId(tokenEntity.getId())
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getCustomFeesForToken(getAddressFromEntity(tokenEntity));

        final var expectedFee = new PrecompileTestContractHistorical.RoyaltyFee(
                BigInteger.valueOf(20L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(100L),
                EntityIdUtils.asHexedEvmAddress(
                        new Id(tokenEntity.getShard(), tokenEntity.getRealm(), tokenEntity.getNum())),
                false,
                Address.fromHexString(
                                Bytes.wrap(collectorAccount.getEvmAddress()).toHexString())
                        .toHexString());

        // Then
        assertThat(functionCall.send().component3().getFirst()).isEqualTo(expectedFee);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getExpiryForToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var expiryPeriod = 9999999999999L;
        final var autoRenewExpiry = 100000000L;
        final var autoRenewAccount = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .autoRenewAccountId(autoRenewAccount.getId())
                        .expirationTimestamp(expiryPeriod)
                        .timestampRange(historicalRange)
                        .autoRenewPeriod(autoRenewExpiry))
                .persist();
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_getExpiryInfoForToken(getAddressFromEntity(tokenEntity));

        final var expectedExpiry = new PrecompileTestContractHistorical.Expiry(
                BigInteger.valueOf(expiryPeriod).divide(BigInteger.valueOf(1_000_000_000L)),
                Address.fromHexString(
                                Bytes.wrap(autoRenewAccount.getEvmAddress()).toHexString())
                        .toHexString(),
                BigInteger.valueOf(autoRenewExpiry));

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedExpiry);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getApproved(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var approvedAccount = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .freezeDefault(true)
                        .timestampRange(historicalRange))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .timestampRange(historicalRange)
                        .spender(approvedAccount.toEntityId()))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsGetApproved(getAddressFromEntity(tokenEntity), BigInteger.ONE);
        final var result = functionCall.send();

        // Then
        assertThat(result).isEqualTo(SENDER_ALIAS.toHexString());
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getAllowanceForToken(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var amountGranted = 50L;
        final var owner = persistAccountEntityHistorical(historicalRange);
        final var spender = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);

        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(amountGranted)
                        .amountGranted(amountGranted)
                        .timestampRange(historicalRange))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsAllowance(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(amountGranted));
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void isApprovedForAllNFT(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var owner = persistAccountEntityHistorical(historicalRange);
        final var spender = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = persistNftHistorical(historicalRange);

        domainBuilder
                .nftAllowance()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .timestampRange(historicalRange)
                        .approvedForAll(true))
                .persist();

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var functionCall = contract.call_htsIsApprovedForAll(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), getAliasFromEntity(spender));

        // Then
        assertThat(functionCall.send()).isEqualTo(Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getFungibleTokenInfo(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenSupply = 900L;

        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(historicalRange))
                .persist();
        final var treasury =
                accountPersistWithBalanceHistorical(tokenSupply, tokenEntity.toEntityId(), historicalRange);
        final var feeCollector = persistAccountEntityHistorical(historicalRange);

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId())
                        .timestampRange(historicalRange)
                        .totalSupply(tokenSupply))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollectorHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForFungibleToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury);

        final var fixedFees = new ArrayList<FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, royaltyFees);

        final var expectedFungibleTokenInfo =
                new FungibleTokenInfo(expectedTokenInfo, BigInteger.valueOf(token.getDecimals()));

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedFungibleTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getNonFungibleTokenInfo(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);

        final var owner = persistAccountEntityHistorical(historicalRange);
        final var treasury = persistAccountEntityHistorical(historicalRange);
        final var feeCollector = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId())
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint())
                        .totalSupply(1L))
                .persist();
        final var nft = domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .accountId(owner.toEntityId())
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollectorHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForNonFungibleToken(
                        getAddressFromEntity(tokenEntity), BigInteger.ONE)
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, royaltyFees);

        final var expectedNonFungibleTokenInfo = new NonFungibleTokenInfo(
                expectedTokenInfo,
                BigInteger.valueOf(nft.getSerialNumber()),
                getAddressFromEntityId(owner.toEntityId()),
                BigInteger.valueOf(token.getCreatedTimestamp()).divide(BigInteger.valueOf(1_000_000_000L)),
                nft.getMetadata(),
                Address.ZERO.toHexString());

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedNonFungibleTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenInfoFungible(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var tokenSupply = 1000L;

        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(historicalRange))
                .persist();
        final var treasury =
                accountPersistWithBalanceHistorical(tokenSupply, tokenEntity.toEntityId(), historicalRange);
        final var feeCollector = persistAccountEntityHistorical(historicalRange);

        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .treasuryAccountId(treasury.toEntityId())
                        .timestampRange(historicalRange)
                        .totalSupply(tokenSupply))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollectorHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.FUNGIBLE_COMMON, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var fractionalFees = new ArrayList<PrecompileTestContractHistorical.FractionalFee>();
        fractionalFees.add(getFractionalFee(customFees.getFractionalFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, fractionalFees, Collections.emptyList());

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedTokenInfo);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 49})
    void getTokenInfoNonFungible(long blockNumber) throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var treasury = persistAccountEntityHistorical(historicalRange);
        final var feeCollector = persistAccountEntityHistorical(historicalRange);
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN)
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury.toEntityId())
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint())
                        .totalSupply(1L))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(1L)
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();

        final var customFees = persistCustomFeesWithFeeCollectorHistorical(
                feeCollector, tokenEntity, TokenTypeEnum.NON_FUNGIBLE_UNIQUE, historicalRange);

        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        // When
        final var result = contract.call_getInformationForToken(getAddressFromEntity(tokenEntity))
                .send();

        final var expectedHederaToken = createExpectedHederaToken(tokenEntity, token, treasury);

        final var fixedFees = new ArrayList<PrecompileTestContractHistorical.FixedFee>();
        fixedFees.add(getFixedFee(customFees.getFixedFees().getFirst(), feeCollector));

        final var royaltyFees = new ArrayList<PrecompileTestContractHistorical.RoyaltyFee>();
        royaltyFees.add(getRoyaltyFee(customFees.getRoyaltyFees().getFirst(), feeCollector));

        final var expectedTokenInfo = createExpectedTokenInfo(
                expectedHederaToken, token, tokenEntity, fixedFees, Collections.emptyList(), royaltyFees);

        // Then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedTokenInfo);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, KYC_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, WIPE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, ECDSA_SECPK256K1, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, ED25519, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, ED25519, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, WIPE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, WIPE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, ED25519, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, ED25519, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, ED25519, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, WIPE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, CONTRACT_ID, PAUSE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 50
                                FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, KYC_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, ED25519, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, CONTRACT_ID, PAUSE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, ADMIN_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FREEZE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, WIPE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, SUPPLY_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, FEE_SCHEDULE_KEY, 49
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 50
                                NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID, PAUSE_KEY, 49
                            """)
    void getTokenKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType,
            final long blockNumber)
            throws Exception {
        // Given
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var contract = testWeb3jService.deploy(PrecompileTestContractHistorical::deploy);

        final var tokenEntity = getTokenWithKey(tokenType, keyValueType, keyType, contract, historicalRange);

        // When
        final var functionCall =
                contract.call_getTokenKeyPublic(getAddressFromEntity(tokenEntity), keyType.getKeyTypeNumeric());

        final var expectedKey = getKeyValueForType(keyValueType, contract.getContractAddress());

        // Then
        assertThat(functionCall.send()).isEqualTo(expectedKey);
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void evmPrecompileReadOnlyTokenFunctionsEthCallHistoricalNotExistingBlockTest(final long blockNumber) {
        testWeb3jService.setUseContractCallDeploy(true);
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        assertThatThrownBy(() -> testWeb3jService.deploy(PrecompileTestContractHistorical::deploy))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(UNKNOWN_BLOCK_NUMBER);
    }

    private Entity getTokenWithKey(
            final TokenTypeEnum tokenType,
            final KeyValueType keyValueType,
            final AbstractContractCallServiceTest.KeyType keyType,
            final Contract contract,
            final Range<Long> historicalRange) {
        final Key key =
                switch (keyValueType) {
                    case ECDSA_SECPK256K1 -> KEY_WITH_ECDSA_TYPE;
                    case ED25519 -> KEY_WITH_ED_25519_TYPE;
                    case CONTRACT_ID -> getKeyWithContractId(contract);
                    case DELEGATABLE_CONTRACT_ID -> getKeyWithDelegatableContractId(contract);
                    default -> throw new IllegalArgumentException("Invalid key type");
                };

        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).key(key.toByteArray()).timestampRange(historicalRange))
                .persist();
        final var tokenBuilder = domainBuilder.token().customize(t -> t.tokenId(tokenEntity.getId())
                .type(tokenType));

        switch (keyType) {
            case ADMIN_KEY:
                break;
            case KYC_KEY:
                tokenBuilder.customize(t -> t.kycKey(key.toByteArray()));
                break;
            case FREEZE_KEY:
                tokenBuilder.customize(t -> t.freezeKey(key.toByteArray()));
                break;
            case WIPE_KEY:
                tokenBuilder.customize(t -> t.wipeKey(key.toByteArray()));
                break;
            case SUPPLY_KEY:
                tokenBuilder.customize(t -> t.supplyKey(key.toByteArray()));
                break;
            case FEE_SCHEDULE_KEY:
                tokenBuilder.customize(t -> t.feeScheduleKey(key.toByteArray()));
                break;
            case PAUSE_KEY:
                tokenBuilder.customize(t -> t.pauseKey(key.toByteArray()));
                break;
            default:
                throw new IllegalArgumentException("Invalid key type");
        }

        tokenBuilder.persist();
        return tokenEntity;
    }

    private Entity persistTokenEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(timestampRange))
                .persist();
    }

    private void persistFungibleTokenHistorical(Entity tokenEntity, final Range<Long> timestampRange) {
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    private Entity persistNftHistorical(final Range<Long> timestampRange) {
        final var tokenEntity = persistTokenEntityHistorical(timestampRange);
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(timestampRange))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenEntity.getId()).serialNumber(1L).timestampRange(timestampRange))
                .persist();

        return tokenEntity;
    }

    private Entity persistAccountEntityHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .deleted(false)
                        .balance(1_000_000_000_000L)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    private Entity persistAccountEntityHistoricalWithAlias(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .deleted(false)
                        .evmAddress(SENDER_ALIAS.toArray())
                        .balance(1_000_000_000_000L)
                        .createdTimestamp(timestampRange.lowerEndpoint())
                        .timestampRange(timestampRange))
                .persist();
    }

    private CustomFee persistCustomFeesWithFeeCollectorHistorical(
            final Entity feeCollector,
            final Entity tokenEntity,
            final TokenTypeEnum tokenType,
            final Range<Long> timestampRange) {
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(1L)
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .fallbackFee(fallbackFee)
                        .numerator(domainBuilder.number())
                        .build()
                : null;

        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .fractionalFees(List.of(fractionalFee))
                            .royaltyFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        } else if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)) {
            return domainBuilder
                    .customFee()
                    .customize(f -> f.tokenId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .royaltyFees(List.of(royaltyFee))
                            .fractionalFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        }

        return CustomFee.builder().build();
    }

    private List<TokenKey> getExpectedTokenKeys(final Entity tokenEntity, final Token token) {
        final var expectedTokenKeys = new ArrayList<TokenKey>();
        expectedTokenKeys.add(getTokenKey(BigInteger.ONE, getKeyValue(tokenEntity.getKey())));
        expectedTokenKeys.add(getTokenKey(KYC_KEY.keyTypeNumeric, getKeyValue(token.getKycKey())));
        expectedTokenKeys.add(getTokenKey(FREEZE_KEY.keyTypeNumeric, getKeyValue(token.getFreezeKey())));
        expectedTokenKeys.add(getTokenKey(WIPE_KEY.keyTypeNumeric, getKeyValue(token.getWipeKey())));
        expectedTokenKeys.add(getTokenKey(SUPPLY_KEY.keyTypeNumeric, getKeyValue(token.getSupplyKey())));
        expectedTokenKeys.add(getTokenKey(FEE_SCHEDULE_KEY.keyTypeNumeric, getKeyValue(token.getFeeScheduleKey())));
        expectedTokenKeys.add(getTokenKey(PAUSE_KEY.keyTypeNumeric, getKeyValue(token.getPauseKey())));

        return expectedTokenKeys;
    }

    private FixedFee getFixedFee(
            final com.hedera.mirror.common.domain.token.FixedFee fixedFee, final Entity feeCollector) {
        return new FixedFee(
                BigInteger.valueOf(fixedFee.getAmount()),
                getAddressFromEntityId(fixedFee.getDenominatingTokenId()),
                false,
                false,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContractHistorical.FractionalFee getFractionalFee(
            final com.hedera.mirror.common.domain.token.FractionalFee fractionalFee, final Entity feeCollector) {
        return new PrecompileTestContractHistorical.FractionalFee(
                BigInteger.valueOf(fractionalFee.getNumerator()),
                BigInteger.valueOf(fractionalFee.getDenominator()),
                BigInteger.valueOf(fractionalFee.getMinimumAmount()),
                BigInteger.valueOf(fractionalFee.getMaximumAmount()),
                true,
                getAliasFromEntity(feeCollector));
    }

    private PrecompileTestContractHistorical.RoyaltyFee getRoyaltyFee(
            final com.hedera.mirror.common.domain.token.RoyaltyFee royaltyFee, final Entity feeCollector) {
        return new PrecompileTestContractHistorical.RoyaltyFee(
                BigInteger.valueOf(royaltyFee.getNumerator()),
                BigInteger.valueOf(royaltyFee.getDenominator()),
                BigInteger.valueOf(royaltyFee.getFallbackFee().getAmount()),
                getAddressFromEntityId(royaltyFee.getFallbackFee().getDenominatingTokenId()),
                false,
                getAddressFromEvmAddress(feeCollector.getEvmAddress()));
    }

    private KeyValue getKeyValue(final byte[] serializedKey) {
        try {
            final var key = Key.parseFrom(serializedKey);
            return getKeyValue(
                    false,
                    key.getContractID().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getContractID())
                                    .toHexString()
                            : Address.ZERO.toHexString(),
                    key.getEd25519().toByteArray(),
                    key.getECDSASecp256K1().toByteArray(),
                    key.getDelegatableContractId().hasContractNum()
                            ? EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractId())
                                    .toHexString()
                            : Address.ZERO.toHexString());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse key", e);
        }
    }

    private Entity accountPersistWithBalanceHistorical(
            final long balance, final EntityId token, final Range<Long> timestampRange) {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.balance(balance)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), EntityId.of(2)))
                        .balance(balance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId()))
                        .balance(balance))
                .persist();

        domainBuilder
                .tokenBalance()
                .customize(ab -> ab.id(new TokenBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId(), token))
                        .balance(balance))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(ab -> ab.id(new TokenTransfer.Id(entity.getCreatedTimestamp(), entity.toEntityId(), token))
                        .amount(100))
                .persist();
        return entity;
    }

    private Range<Long> setUpHistoricalContext(final long blockNumber) {
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        final var historicalRange = Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd());
        testWeb3jService.setHistoricalRange(historicalRange);
        return historicalRange;
    }

    private HederaToken createExpectedHederaToken(final Entity tokenEntity, final Token token, final Entity treasury) {
        final var expectedTokenKeys = getExpectedTokenKeys(tokenEntity, token);

        final var expectedExpiry = (Expiry) getTokenExpiry(
                tokenEntity.getExpirationTimestamp() / 1_000_000_000L,
                Address.ZERO.toHexString(),
                tokenEntity.getAutoRenewPeriod());
        return getHederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEvmAddress(treasury.getEvmAddress()),
                tokenEntity.getMemo(),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                expectedTokenKeys,
                expectedExpiry);
    }

    private TokenInfo createExpectedTokenInfo(
            HederaToken expectedHederaToken,
            Token token,
            Entity tokenEntity,
            List<FixedFee> fixedFees,
            List<PrecompileTestContractHistorical.FractionalFee> fractionalFees,
            List<PrecompileTestContractHistorical.RoyaltyFee> royaltyFees) {
        return new TokenInfo(
                expectedHederaToken,
                BigInteger.valueOf(token.getTotalSupply()),
                tokenEntity.getDeleted(),
                false,
                false,
                fixedFees,
                fractionalFees,
                royaltyFees,
                LEDGER_ID);
    }

    private void persistTokenRelationshipWithKycGrantedHistorical(
            final Entity tokenEntity, final Entity account, final Range<Long> historicalRange) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
    }

    private Pair<Entity, Entity> persistAccountTokenAndFrozenRelationshipHistorical(final Range<Long> historicalRange) {
        final var account = persistAccountEntityHistoricalWithAlias(historicalRange);
        final var tokenEntity = persistTokenEntityHistorical(historicalRange);
        persistFungibleTokenHistorical(tokenEntity, historicalRange);
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
        return Pair.of(account, tokenEntity);
    }
}
