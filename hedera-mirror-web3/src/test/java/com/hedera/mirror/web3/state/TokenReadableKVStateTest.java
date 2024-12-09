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

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.pbj.runtime.OneOf;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenReadableKVStateTest {

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1252L).build();
    private static final TokenID TOKEN_ID_FOR_NEGATIVE_TEST =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1253L).build();
    private static final Long TOKEN_ENCODED_ID = EntityId.of(
                    TOKEN_ID.shardNum(), TOKEN_ID.realmNum(), TOKEN_ID.tokenNum())
            .getId();
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final TokenID DENOMINATING_TOKEN_ID =
            TokenID.newBuilder().shardNum(11L).realmNum(12L).tokenNum(13L).build();
    private static MockedStatic<ContractCallContext> contextMockedStatic;
    private final EntityId collectorId = EntityId.of(1L, 2L, 3L);
    private final AccountID collectorAccountId = new AccountID(
            collectorId.getShard(),
            collectorId.getRealm(),
            new OneOf<>(AccountOneOfType.ACCOUNT_NUM, collectorId.getNum()));
    private final EntityId denominatingTokenId = EntityId.of(11L, 12L, 13L);
    com.hedera.mirror.common.domain.token.Token databaseToken;
    private CustomFee customFee;

    @InjectMocks
    private TokenReadableKVState tokenReadableKVState;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private EntityRepository entityRepository;

    private DomainBuilder domainBuilder;
    private Entity entity;
    private Entity account;
    private Entity collector;

    @Spy
    private ContractCallContext contractCallContext;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        entity = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(TOKEN_ID.tokenNum());
                    e.num(TOKEN_ID.tokenNum());
                    e.type(EntityType.TOKEN);
                })
                .get();
        account = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(TOKEN_ID_FOR_NEGATIVE_TEST.tokenNum());
                    e.num(TOKEN_ID_FOR_NEGATIVE_TEST.tokenNum());
                    e.type(EntityType.ACCOUNT);
                })
                .get();
        collector = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(collectorId.getId());
                    e.shard(collectorId.getShard());
                    e.realm(collectorId.getRealm());
                    e.num(collectorId.getNum());
                    e.type(EntityType.ACCOUNT);
                    e.evmAddress(null);
                    e.alias(null);
                })
                .get();
        customFee = new CustomFee();

        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void getTokenMappedValues() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token)
                .returns(TOKEN_ID, Token::tokenId)
                .returns(
                        com.hedera.hapi.node.base.TokenType.valueOf(
                                databaseToken.getType().name()),
                        Token::tokenType)
                .returns(
                        com.hedera.hapi.node.base.TokenSupplyType.valueOf(
                                databaseToken.getSupplyType().name()),
                        Token::supplyType)
                .returns(databaseToken.getMaxSupply(), Token::maxSupply)
                .returns(databaseToken.getFreezeDefault(), Token::accountsFrozenByDefault)
                .returns(false, Token::deleted)
                .returns(false, Token::paused)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Token::expirationSecond)
                .returns(entity.getMemo(), Token::memo)
                .returns(databaseToken.getName(), Token::name)
                .returns(databaseToken.getSymbol(), Token::symbol)
                .returns(databaseToken.getDecimals(), Token::decimals)
                .returns(entity.getAutoRenewPeriod(), Token::autoRenewSeconds);

        assertThat(token.totalSupplySupplier().get()).isEqualTo(databaseToken.getTotalSupply());
    }

    @Test
    void getTokenReturnsNullWhenIdIsAnAccount() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID_FOR_NEGATIVE_TEST, Optional.empty()))
                .thenReturn(Optional.ofNullable(account));
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID_FOR_NEGATIVE_TEST))
                .isNull();
    }

    @Test
    void getTokenMappedValuesHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token)
                .returns(TOKEN_ID, Token::tokenId)
                .returns(TokenType.valueOf(databaseToken.getType().name()), Token::tokenType)
                .returns(TokenSupplyType.valueOf(databaseToken.getSupplyType().name()), Token::supplyType)
                .returns(databaseToken.getMaxSupply(), Token::maxSupply)
                .returns(databaseToken.getFreezeDefault(), Token::accountsFrozenByDefault)
                .returns(false, Token::deleted)
                .returns(false, Token::paused)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Token::expirationSecond)
                .returns(entity.getMemo(), Token::memo)
                .returns(databaseToken.getName(), Token::name)
                .returns(databaseToken.getSymbol(), Token::symbol)
                .returns(databaseToken.getDecimals(), Token::decimals)
                .returns(entity.getAutoRenewPeriod(), Token::autoRenewSeconds);

        assertThat(token.totalSupplySupplier().get()).isZero();
    }

    @Test
    void getCustomFees() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.customFeesSupplier().get()).isEqualTo(Collections.emptyList()));
        verify(customFeeRepository).findById(TOKEN_ID.tokenNum());
    }

    @Test
    void getCustomFeesHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.customFeesSupplier().get()).isEqualTo(Collections.emptyList()));
    }

    @Test
    void getPartialTreasuryAccount() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());

        Entity treasuryEntity = mock(Entity.class);
        EntityId treasuryEntityId = mock(EntityId.class);
        when(treasuryEntityId.getShard()).thenReturn(11L);
        when(treasuryEntityId.getRealm()).thenReturn(12L);
        when(treasuryEntityId.getNum()).thenReturn(13L);

        when(treasuryEntity.toEntityId()).thenReturn(treasuryEntityId);
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(entityRepository.findByIdAndDeletedIsFalse(
                        databaseToken.getTreasuryAccountId().getId()))
                .thenReturn(Optional.of(treasuryEntity));

        final var expectedTreasuryAccountId = toAccountId(11L, 12L, 13L);

        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);

        assertThat(token.treasuryAccountIdSupplier().get()).isEqualTo(expectedTreasuryAccountId);
        verify(entityRepository)
                .findByIdAndDeletedIsFalse(databaseToken.getTreasuryAccountId().getId());
    }

    @Test
    void getPartialTreasuryAccountHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);

        Entity treasuryEntity = mock(Entity.class);
        EntityId treasuryEntityId = mock(EntityId.class);
        when(treasuryEntityId.getShard()).thenReturn(0L);
        when(treasuryEntityId.getRealm()).thenReturn(0L);
        when(treasuryEntityId.getNum()).thenReturn(10L);
        when(treasuryEntity.toEntityId()).thenReturn(treasuryEntityId);

        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(entityRepository.findActiveByIdAndTimestamp(treasuryEntity.getId(), timestamp.get()))
                .thenReturn(Optional.of(treasuryEntity));

        final var expectedTreasuryAccountId = toAccountId(0L, 0L, 10L);
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token ->
                        assertThat(token.treasuryAccountIdSupplier().get()).isEqualTo(expectedTreasuryAccountId));

        verify(entityRepository).findActiveByIdAndTimestamp(treasuryEntity.getId(), timestamp.get());
    }

    @Test
    void getAutoRenewAccount() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        databaseToken.setTreasuryAccountId(null);

        Entity autorenewEntity = mock(Entity.class);
        entity.setAutoRenewAccountId(10L);
        EntityId autorenewEntityId = mock(EntityId.class);
        when(autorenewEntityId.getShard()).thenReturn(0L);
        when(autorenewEntityId.getRealm()).thenReturn(0L);
        when(autorenewEntityId.getNum()).thenReturn(10L);
        when(autorenewEntity.toEntityId()).thenReturn(autorenewEntityId);
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(entityRepository.findByIdAndDeletedIsFalse(entity.getAutoRenewAccountId()))
                .thenReturn(Optional.of(autorenewEntity));

        verify(entityRepository, never()).findByIdAndDeletedIsFalse(anyLong());

        final var autoRenewAccountId = toAccountId(0L, 0L, 10L);
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token ->
                        assertThat(token.autoRenewAccountIdSupplier().get()).isEqualTo(autoRenewAccountId));

        verify(entityRepository).findByIdAndDeletedIsFalse(entity.getAutoRenewAccountId());
    }

    @Test
    void getAutoRenewAccountHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        databaseToken.setTreasuryAccountId(null);

        Entity autorenewEntity = mock(Entity.class);
        EntityId autorenewEntityId = mock(EntityId.class);
        when(autorenewEntityId.getShard()).thenReturn(0L);
        when(autorenewEntityId.getRealm()).thenReturn(0L);
        when(autorenewEntityId.getNum()).thenReturn(10L);
        when(autorenewEntity.toEntityId()).thenReturn(autorenewEntityId);

        entity.setAutoRenewAccountId(10L);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(entityRepository.findActiveByIdAndTimestamp(entity.getAutoRenewAccountId(), timestamp.get()))
                .thenReturn(Optional.of(autorenewEntity));

        verify(entityRepository, never()).findActiveByIdAndTimestamp(anyLong(), anyLong());

        final var autoRenewAccountId = toAccountId(0L, 0L, 10L);
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token ->
                        assertThat(token.autoRenewAccountIdSupplier().get()).isEqualTo(autoRenewAccountId));

        verify(entityRepository).findActiveByIdAndTimestamp(entity.getAutoRenewAccountId(), timestamp.get());
    }

    @Test
    void getTotalSupplyHistoricalFungible() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        final var treasuryId = EntityId.of(123L);
        final var totalSupply = 10L;
        final var historicalSupply = 9L;
        databaseToken.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        databaseToken.setTotalSupply(totalSupply);
        databaseToken.setTreasuryAccountId(treasuryId);

        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get()))
                .thenReturn(historicalSupply);

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(
                        token -> assertThat(token.totalSupplySupplier().get()).isEqualTo(historicalSupply));

        verify(tokenRepository)
                .findFungibleTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get());
    }

    @Test
    void getTotalSupplyHistoricalNonFungible() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        final var treasuryId = EntityId.of(123L);
        final var totalSupply = 10L;
        final var historicalSupply = 9L;
        databaseToken.setType(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        databaseToken.setTotalSupply(totalSupply);
        databaseToken.setTreasuryAccountId(treasuryId);

        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(nftRepository.findNftTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get()))
                .thenReturn(historicalSupply);

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(
                        token -> assertThat(token.totalSupplySupplier().get()).isEqualTo(historicalSupply));

        verify(nftRepository).findNftTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get());
    }

    @Test
    void getNullOnMissingToken() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.empty());
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void getNullOnMissingTokenDHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.empty());
        assertThat(tokenReadableKVState.get(TOKEN_ID)).isNull();
    }

    @Test
    void getTokenKeysValues() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).satisfies(token -> assertThat(token)
                .returns(Utils.parseKey(entity.getKey()), Token::adminKey)
                .returns(Utils.parseKey(databaseToken.getKycKey()), Token::kycKey)
                .returns(Utils.parseKey(databaseToken.getPauseKey()), Token::pauseKey)
                .returns(Utils.parseKey(databaseToken.getFreezeKey()), Token::freezeKey)
                .returns(Utils.parseKey(databaseToken.getWipeKey()), Token::wipeKey)
                .returns(Utils.parseKey(databaseToken.getSupplyKey()), Token::supplyKey)
                .returns(Utils.parseKey(databaseToken.getFeeScheduleKey()), Token::feeScheduleKey));
    }

    @Test
    void getTokenKeysValuesHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).satisfies(token -> assertThat(token)
                .returns(Utils.parseKey(entity.getKey()), com.hedera.hapi.node.state.token.Token::adminKey)
                .returns(Utils.parseKey(databaseToken.getKycKey()), Token::kycKey)
                .returns(Utils.parseKey(databaseToken.getPauseKey()), Token::pauseKey)
                .returns(Utils.parseKey(databaseToken.getFreezeKey()), Token::freezeKey)
                .returns(Utils.parseKey(databaseToken.getWipeKey()), Token::wipeKey)
                .returns(Utils.parseKey(databaseToken.getSupplyKey()), Token::supplyKey)
                .returns(Utils.parseKey(databaseToken.getFeeScheduleKey()), Token::feeScheduleKey));
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFound() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFoundHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findByTokenIdAndTimestamp(entity.getId(), timestamp.get()))
                .thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void keyIsNullIfNotParsable() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());

        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        databaseToken.setKycKey("wrOng".getBytes());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.kycKey()).isNull());
    }

    @Test
    void royaltyFee() {
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.fractionalFee()).isNull();
                var resultRoyaltyFee = fee.royaltyFee();
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().amount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().denominatingTokenId())
                        .isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void royaltyFeeHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.fractionalFee()).isNull();
                var resultRoyaltyFee = fee.royaltyFee();
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().amount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().denominatingTokenId())
                        .isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void royaltyFeeNoFallback() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var fee = results.get(0);
        var resultFee = fee.royaltyFee();
        assertEquals(
                royaltyFee.getNumerator(), resultFee.exchangeValueFraction().numerator());
        assertEquals(
                royaltyFee.getDenominator(), resultFee.exchangeValueFraction().denominator());
        assertEquals(
                0L, resultFee.fallbackFee() != null ? resultFee.fallbackFee().amount() : 0L);
        assertEquals(collectorAccountId, fee.feeCollectorAccountId());
        assertEquals(
                TokenID.DEFAULT,
                resultFee.fallbackFee() != null ? resultFee.fallbackFee().denominatingTokenId() : TokenID.DEFAULT);
    }

    @Test
    void royaltyFeeNoFallbackHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var fee = results.get(0);
        var resultFee = fee.royaltyFee();
        assertEquals(
                royaltyFee.getNumerator(), resultFee.exchangeValueFraction().numerator());
        assertEquals(
                royaltyFee.getDenominator(), resultFee.exchangeValueFraction().denominator());
        assertEquals(
                0L, resultFee.fallbackFee() != null ? resultFee.fallbackFee().amount() : 0L);
        assertEquals(collectorAccountId, fee.feeCollectorAccountId());
        assertEquals(
                TokenID.DEFAULT,
                resultFee.fallbackFee() != null ? resultFee.fallbackFee().denominatingTokenId() : TokenID.DEFAULT);
    }

    @Test
    void fractionFee() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFractionalFee = fee.fractionalFee();
                Assertions.assertThat(resultFractionalFee.fractionalAmount().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultFractionalFee.fractionalAmount().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultFractionalFee.maximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                Assertions.assertThat(resultFractionalFee.minimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                Assertions.assertThat(resultFractionalFee.netOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fractionFeeHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFractionalFee = fee.fractionalFee();
                Assertions.assertThat(resultFractionalFee.fractionalAmount().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultFractionalFee.fractionalAmount().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultFractionalFee.maximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                Assertions.assertThat(resultFractionalFee.minimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                Assertions.assertThat(resultFractionalFee.netOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fixedFee() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fractionalFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFixedFee = fee.fixedFee();
                Assertions.assertThat(resultFixedFee.amount()).isEqualTo(domainFee.getAmount());
                Assertions.assertThat(resultFixedFee.denominatingTokenId()).isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(resultFixedFee.hasDenominatingTokenId()).isTrue();
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fixedFeeHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fractionalFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFixedFee = fee.fixedFee();
                Assertions.assertThat(resultFixedFee.amount()).isEqualTo(domainFee.getAmount());
                Assertions.assertThat(resultFixedFee.denominatingTokenId()).isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(resultFixedFee.hasDenominatingTokenId()).isTrue();
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void mapOnlyFeesWithCollectorAccountId() {
        when(ContractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(noCollectorCustomFee));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void mapOnlyFeesWithCollectorAccountIdHistorical() {
        when(ContractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(noCollectorCustomFee));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void sizeIsAlwaysEmpty() {
        assertThat(tokenReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(tokenReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    private void setupToken(Optional<Long> timestamp) {
        databaseToken =
                domainBuilder.token().customize(t -> t.tokenId(entity.getId())).get();
        final var treasuryId = mock(EntityId.class);
        databaseToken.setTreasuryAccountId(treasuryId);
        if (timestamp.isPresent()) {
            when(tokenRepository.findByTokenIdAndTimestamp(entity.getId(), timestamp.get()))
                    .thenReturn(Optional.ofNullable(databaseToken));
        } else {
            when(tokenRepository.findById(any())).thenReturn(Optional.ofNullable(databaseToken));
        }
    }
}
