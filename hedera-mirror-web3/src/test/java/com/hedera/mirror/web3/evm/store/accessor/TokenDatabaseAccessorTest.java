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

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.Collections;
import java.util.Optional;
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

    @InjectMocks
    private TokenDatabaseAccessor tokenDatabaseAccessor;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    com.hedera.mirror.common.domain.token.Token databaseToken;

    private Entity entity;

    private static final long SHARD = 0L;

    private static final long REALM = 1L;
    private static final long EXPIRATION_TIMESTAMP = 2L;

    private static final long TOTAL_SUPPLY = 3L;
    private static final long AUTO_RENEW_PERIOD = 4L;

    private static final int DECIMALS = 5;

    private static final long MAX_SUPPLY = 6L;

    private static final boolean FREEZE_DEFAULT = false;

    private static final String MEMO = "memo1";
    private static final String NAME = "name1";
    private static final String SYMBOL = "symbol1";

    private final Key KYC_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27a"))
            .build();
    private final Key FREEZE_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27b"))
            .build();
    private final Key SUPPLY_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27c"))
            .build();
    private final Key WIPE_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"))
            .build();
    private final Key FEE_SCHEDULE_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27e"))
            .build();

    private final Key PAUSE_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27f"))
            .build();

    private final Key ADMIN_KEY = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb271"))
            .build();

    @BeforeEach
    void setup() {
        final var entityNum = entityIdNumFromEvmAddress(ADDRESS);
        entity = new Entity();
        entity.setId(entityNum);
        entity.setShard(SHARD);
        entity.setRealm(REALM);
        entity.setNum(entityNum);
        entity.setDeleted(false);
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD);
        entity.setExpirationTimestamp(EXPIRATION_TIMESTAMP);
        entity.setMemo(MEMO);
        entity.setKey(ADMIN_KEY.toByteArray());
        when(entityDatabaseAccessor.get(any())).thenReturn(Optional.ofNullable(entity));
    }

    @Test
    void getTokenMappeddValues() {
        setupToken();

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token)
                .returns(new Id(entity.getShard(), entity.getRealm(), entity.getNum()), Token::getId)
                .returns(TokenType.NON_FUNGIBLE_UNIQUE, Token::getType)
                .returns(TokenSupplyType.FINITE, Token::getSupplyType)
                .returns(TOTAL_SUPPLY, Token::getTotalSupply)
                .returns(MAX_SUPPLY, Token::getMaxSupply)
                .returns(FREEZE_DEFAULT, Token::isFrozenByDefault)
                .returns(false, Token::isDeleted)
                .returns(false, Token::isPaused)
                .returns(EXPIRATION_TIMESTAMP, Token::getExpiry)
                .returns(MEMO, Token::getMemo)
                .returns(NAME, Token::getName)
                .returns(SYMBOL, Token::getSymbol)
                .returns(DECIMALS, Token::getDecimals)
                .returns(AUTO_RENEW_PERIOD, Token::getAutoRenewPeriod));
    }

    @Test
    void getPartialTreasuryAccount() {
        setupToken();
        final var treasuryId = mock(EntityId.class);
        databaseToken.setTreasuryAccountId(treasuryId);

        Entity treasuryEntity = mock(Entity.class);
        when(treasuryEntity.getShard()).thenReturn(11L);
        when(treasuryEntity.getRealm()).thenReturn(12L);
        when(treasuryEntity.getNum()).thenReturn(13L);
        when(treasuryEntity.getBalance()).thenReturn(14L);
        when(entityDatabaseAccessor.getById(treasuryId.getId())).thenReturn(Optional.of(treasuryEntity));

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token.getTreasury())
                .returns(new Id(11, 12, 13), Account::getId)
                .returns(14L, Account::getBalance));
    }

    @Test
    void getTokenDefaultValues() {
        setupToken();

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token)
                .returns(Collections.emptyList(), Token::mintedUniqueTokens)
                .returns(Collections.emptyList(), Token::removedUniqueTokens)
                .returns(Collections.emptyMap(), Token::getLoadedUniqueTokens)
                .returns(false, Token::hasChangedSupply)
                .returns(null, Token::getTreasury)
                .returns(null, Token::getAutoRenewAccount)
                .returns(false, Token::isBelievedToHaveBeenAutoRemoved)
                .returns(false, Token::isNew)
                .returns(null, Token::getTreasury)
                .returns(0L, Token::getLastUsedSerialNumber));
    }

    @Test
    void getTokenKeysValues() {
        setupToken();

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).hasValueSatisfying(token -> assertThat(token)
                .returns(asFcKeyUnchecked(ADMIN_KEY), Token::getAdminKey)
                .returns(asFcKeyUnchecked(KYC_KEY), Token::getKycKey)
                .returns(asFcKeyUnchecked(PAUSE_KEY), Token::getPauseKey)
                .returns(asFcKeyUnchecked(FREEZE_KEY), Token::getFreezeKey)
                .returns(asFcKeyUnchecked(WIPE_KEY), Token::getWipeKey)
                .returns(asFcKeyUnchecked(SUPPLY_KEY), Token::getSupplyKey)
                .returns(asFcKeyUnchecked(FEE_SCHEDULE_KEY), Token::getFeeScheduleKey));
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFound() {
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(tokenDatabaseAccessor.get(ADDRESS)).isEmpty();
    }

    @Test
    void keyIsNullIfNotParsable() {
        setupToken();
        databaseToken.setKycKey("wrOng".getBytes());

        assertThat(tokenDatabaseAccessor.get(ADDRESS))
                .hasValueSatisfying(token -> assertThat(token).returns(null, Token::getKycKey));
    }

    private void setupToken() {
        final var tokenId = new TokenId(entity.toEntityId());
        databaseToken = new com.hedera.mirror.common.domain.token.Token();
        databaseToken.setTokenId(tokenId);
        databaseToken.setType(NON_FUNGIBLE_UNIQUE);
        databaseToken.setSupplyType(TokenSupplyTypeEnum.FINITE);
        databaseToken.setTotalSupply(TOTAL_SUPPLY);
        databaseToken.setMaxSupply(MAX_SUPPLY);
        databaseToken.setKycKey(KYC_KEY.toByteArray());
        databaseToken.setFreezeKey(FREEZE_KEY.toByteArray());
        databaseToken.setSupplyKey(SUPPLY_KEY.toByteArray());
        databaseToken.setWipeKey(WIPE_KEY.toByteArray());
        databaseToken.setFeeScheduleKey(FEE_SCHEDULE_KEY.toByteArray());
        databaseToken.setPauseKey(PAUSE_KEY.toByteArray());
        databaseToken.setFreezeDefault(FREEZE_DEFAULT);
        databaseToken.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);
        databaseToken.setName(NAME);
        databaseToken.setSymbol(SYMBOL);
        databaseToken.setDecimals(DECIMALS);
        when(tokenRepository.findById(any())).thenReturn(Optional.ofNullable(databaseToken));
    }
}
