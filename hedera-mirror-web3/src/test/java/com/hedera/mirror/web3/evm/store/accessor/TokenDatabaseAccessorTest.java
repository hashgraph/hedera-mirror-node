/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ADDRESS_ZERO = Address.ZERO;
    com.hedera.mirror.common.domain.token.Token databaseToken;

    @InjectMocks
    private TokenDatabaseAccessor tokenDatabaseAccessor;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private EntityRepository entityRepository;

    private DomainBuilder domainBuilder;

    @Mock
    private Entity defaultEntity;

    private Entity entity;

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityIdNumFromEvmAddress(ADDRESS)))
                .get();
    }

    @Test
    void getTokenMappedValues() {
        setupToken();
        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));
        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token)
                .returns(new Id(entity.getShard(), entity.getRealm(), entity.getNum()), Token::getId)
                .returns(TokenType.valueOf(databaseToken.getType().name()), Token::getType)
                .returns(TokenSupplyType.valueOf(databaseToken.getSupplyType().name()), Token::getSupplyType)
                .returns(databaseToken.getTotalSupply(), Token::getTotalSupply)
                .returns(databaseToken.getMaxSupply(), Token::getMaxSupply)
                .returns(databaseToken.getFreezeDefault(), Token::isFrozenByDefault)
                .returns(false, Token::isDeleted)
                .returns(false, Token::isPaused)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Token::getExpiry)
                .returns(entity.getMemo(), Token::getMemo)
                .returns(databaseToken.getName(), Token::getName)
                .returns(databaseToken.getSymbol(), Token::getSymbol)
                .returns(databaseToken.getDecimals(), Token::getDecimals)
                .returns(entity.getAutoRenewPeriod(), Token::getAutoRenewPeriod));
    }

    @Test
    void getCustomFees() {
        setupToken();
        List<CustomFee> customFees = singletonList(new CustomFee());
        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));
        when(customFeeDatabaseAccessor.get(entity.getId())).thenReturn(Optional.of(customFees));

        assertThat(tokenDatabaseAccessor.get(ADDRESS))
                .hasValueSatisfying(token -> assertThat(token.getCustomFees()).isEqualTo(customFees));
    }

    @Test
    void getPartialTreasuryAccount() {
        setupToken();
        final var treasuryId = mock(EntityId.class);
        databaseToken.setTreasuryAccountId(treasuryId);

        Entity treasuryEntity = mock(Entity.class);
        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));
        when(treasuryEntity.getShard()).thenReturn(11L);
        when(treasuryEntity.getRealm()).thenReturn(12L);
        when(treasuryEntity.getNum()).thenReturn(13L);
        when(treasuryEntity.getBalance()).thenReturn(14L);
        when(entityRepository.findByIdAndDeletedIsFalse(treasuryId.getId())).thenReturn(Optional.of(treasuryEntity));

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token.getTreasury())
                .returns(new Id(11, 12, 13), Account::getId)
                .returns(14L, Account::getBalance));
    }

    @Test
    void getTokenDefaultValues() {
        setupToken();
        databaseToken.setTreasuryAccountId(null);
        when(entityDatabaseAccessor.get(ADDRESS_ZERO)).thenReturn(Optional.ofNullable(defaultEntity));
        when(defaultEntity.getShard()).thenReturn(0L);
        when(defaultEntity.getRealm()).thenReturn(0L);
        when(defaultEntity.getNum()).thenReturn(0L);
        assertThat(tokenDatabaseAccessor.get(ADDRESS_ZERO)).hasValueSatisfying(token -> assertThat(token)
                .returns(emptyList(), Token::mintedUniqueTokens)
                .returns(emptyList(), Token::removedUniqueTokens)
                .returns(Collections.emptyMap(), Token::getLoadedUniqueTokens)
                .returns(false, Token::hasChangedSupply)
                .returns(null, Token::getTreasury)
                .returns(null, Token::getAutoRenewAccount)
                .returns(false, Token::isBelievedToHaveBeenAutoRemoved)
                .returns(false, Token::isNew)
                .returns(null, Token::getTreasury)
                .returns(0L, Token::getLastUsedSerialNumber)
                .returns(emptyList(), Token::getCustomFees));
    }

    @Test
    void getTokenKeysValues() {
        setupToken();
        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token)
                .returns(parseJkey(entity.getKey()), Token::getAdminKey)
                .returns(parseJkey(databaseToken.getKycKey()), Token::getKycKey)
                .returns(parseJkey(databaseToken.getPauseKey()), Token::getPauseKey)
                .returns(parseJkey(databaseToken.getFreezeKey()), Token::getFreezeKey)
                .returns(parseJkey(databaseToken.getWipeKey()), Token::getWipeKey)
                .returns(parseJkey(databaseToken.getSupplyKey()), Token::getSupplyKey)
                .returns(parseJkey(databaseToken.getFeeScheduleKey()), Token::getFeeScheduleKey));
    }

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFound() {
        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).isEmpty();
    }

    @Test
    void keyIsNullIfNotParsable() {
        setupToken();

        when(entityDatabaseAccessor.get(ADDRESS)).thenReturn(Optional.ofNullable(entity));
        databaseToken.setKycKey("wrOng".getBytes());

        assertThat(tokenDatabaseAccessor.get(ADDRESS))
                .hasValueSatisfying(token -> assertThat(token.getKycKey()).isNull());
    }

    private void setupToken() {
        databaseToken =
                domainBuilder.token().customize(t -> t.tokenId(entity.getId())).get();
        when(tokenRepository.findById(any())).thenReturn(Optional.ofNullable(databaseToken));
    }
}
