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

package com.hedera.mirror.web3.evm.token;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.CustomFeeDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenAccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.UniqueTokenDatabaseAccessor;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TokenAccessorImplTest {

    final byte[] bytes = new byte[33];
    final Key key =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
    private final long serialNo = 0L;
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address TOKEN = Address.fromHexString(HEX);
    private static final EntityId ENTITY = DomainUtils.fromEvmAddress(TOKEN.toArrayUnsafe());
    private static final Long ENTITY_ID =
            EntityIdEndec.encode(ENTITY.getShardNum(), ENTITY.getRealmNum(), ENTITY.getEntityNum());

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MirrorNodeEvmProperties properties;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Entity entity;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Token token;

    private List<DatabaseAccessor<Object, ?>> accessors;

    private Store store;
    private final DomainBuilder domainBuilder = new DomainBuilder();

    public TokenAccessorImpl tokenAccessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        final var entityAccessor = new EntityDatabaseAccessor(entityRepository);
        final var customFeeAccessor = new CustomFeeDatabaseAccessor(customFeeRepository, entityAccessor);
        accessors = List.of(
                entityAccessor,
                customFeeAccessor,
                new TokenAccountDatabaseAccessor(tokenAccountRepository),
                new UniqueTokenDatabaseAccessor(nftRepository),
                new TokenDatabaseAccessor(tokenRepository, entityAccessor, entityRepository, customFeeAccessor));
        store = new StoreImpl(accessors);
        tokenAccessor = new TokenAccessorImpl(properties, store);
    }

    @Test
    void evmNftInfo() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;
        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();
        when(entityRepository.findByIdAndDeletedIsFalse(nft.getAccountId().getId()))
                .thenReturn(Optional.of(entity));
        when(nftRepository.findActiveById(ENTITY_ID, serialNo)).thenReturn(Optional.of(nft));

        final var expected = new EvmNftInfo(
                serialNo, toAddress(nft.getAccountId()), createdTimestampSecs, nft.getMetadata(), Address.ZERO, null);
        final var result = tokenAccessor.evmNftInfo(TOKEN, serialNo);
        assertThat(result).isNotEmpty();
        assertEquals(expected.getSerialNumber(), result.get().getSerialNumber());
        assertEquals(expected.getSpender(), result.get().getSpender());
        assertEquals(expected.getAccount(), result.get().getAccount());
    }

    @Test
    void evmNftInfoNoOwner() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;
        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();
        when(entityRepository.findByIdAndDeletedIsFalse(nft.getAccountId().getId()))
                .thenReturn(Optional.empty());
        when(nftRepository.findActiveById(ENTITY_ID, serialNo)).thenReturn(Optional.of(nft));

        final var expected =
                new EvmNftInfo(serialNo, Address.ZERO, createdTimestampSecs, nft.getMetadata(), Address.ZERO, null);
        final var result = tokenAccessor.evmNftInfo(TOKEN, serialNo);
        assertThat(result).isNotEmpty();
        assertEquals(expected.getSerialNumber(), result.get().getSerialNumber());
        assertEquals(expected.getSpender(), result.get().getSpender());
        assertEquals(expected.getAccount(), result.get().getAccount());
    }

    @Test
    void isTokenAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        assertFalse(tokenAccessor.isTokenAddress(TOKEN));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        assertTrue(tokenAccessor.isTokenAddress(TOKEN));
    }

    @Test
    void isFrozen() {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        assertTrue(tokenAccessor.isFrozen(TOKEN, TOKEN));
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        assertFalse(tokenAccessor.isFrozen(TOKEN, TOKEN));
    }

    @Test
    void isKyc() {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        assertTrue(tokenAccessor.isKyc(TOKEN, TOKEN));
        tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        assertFalse(tokenAccessor.isKyc(TOKEN, TOKEN));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void infoForTokenCustomFees() {
        final var customFee = new CustomFee();
        final EntityId collectorId = new EntityId(1L, 2L, 3L, EntityType.ACCOUNT);
        customFee.setCollectorAccountId(collectorId);
        List customFeeList = List.of(customFee);
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(collectorId.toEntity()));
        when(customFeeRepository.findByTokenId(any())).thenReturn(customFeeList);
        assertThat(tokenAccessor.infoForTokenCustomFees(TOKEN)).isNotEmpty();
        assertEquals(
                toAddress(collectorId),
                tokenAccessor
                        .infoForTokenCustomFees(TOKEN)
                        .get()
                        .get(0)
                        .getFixedFee()
                        .getFeeCollector());
    }

    @Test
    void keyOf() {
        final byte[] bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(token.getWipeKey()).thenReturn(key.toByteArray());
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(entity.getAutoRenewAccountId()).thenReturn(null);
        final var result = tokenAccessor.keyOf(TOKEN, TokenKeyType.WIPE_KEY);
        assertThat(result).isNotNull();
        assertArrayEquals(key.getECDSASecp256K1().toByteArray(), result.getECDSASecp256K1());
    }
}
