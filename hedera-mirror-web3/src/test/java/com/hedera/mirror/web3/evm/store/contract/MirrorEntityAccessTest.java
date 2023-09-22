/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store.contract;

import static com.google.protobuf.ByteString.EMPTY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.datatypes.Address.ZERO;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEntityAccessTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Bytes BYTES = Bytes.fromHexString(HEX);
    private static final byte[] DATA = BYTES.toArrayUnsafe();
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final EntityId ENTITY = DomainUtils.fromEvmAddress(ADDRESS.toArrayUnsafe());
    private static final Long ENTITY_ID =
            EntityId.of(ENTITY.getShard(), ENTITY.getRealm(), ENTITY.getNum()).getId();
    private static final Address NON_MIRROR_ADDRESS =
            Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractStateRepository contractStateRepository;

    @Mock
    private Account account;

    @Mock
    private Token token;

    @Mock
    private Store store;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private MirrorEntityAccess mirrorEntityAccess;

    private long defaultBalance = 23L;

    @BeforeEach
    void setUp() {
        mirrorEntityAccess =
                new MirrorEntityAccess(contractStateRepository, contractRepository, store, mirrorNodeEvmProperties);
    }

    @Test
    void isNotUsableWithNegativeBalance() {
        final long balance = -1L;
        when(store.getAccount(ADDRESS, OnMissing.THROW)).thenReturn(account);
        when(account.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void isNotUsableWithWrongAlias() {
        final var address = Address.fromHexString("0x3232134567785444e");
        when(store.getAccount(address, OnMissing.THROW)).thenThrow(InvalidTransactionException.class);
        final var result = mirrorEntityAccess.isUsable(address);
        assertThat(result).isFalse();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isNotUsableWithExpiredTimestamp() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating; also balance can no longer be `null`")
    @Test
    void isNotUsableWithExpiredTimestampAndNullBalance() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isUsableWithNotExpiredTimestamp() {
        when(account.getBalance()).thenReturn(defaultBalance);
        final long expiredTimestamp = Instant.MAX.getEpochSecond();
        when(store.getAccount(ADDRESS, OnMissing.THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(expiredTimestamp);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isNotUsableWithExpiredAutoRenewTimestamp() {
        final long autoRenewPeriod = Instant.MAX.getEpochSecond();
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isUsableWithNotExpiredAutoRenewTimestamp() {
        when(account.getBalance()).thenReturn(defaultBalance);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(Instant.MAX.getEpochSecond());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isUsableContractWithValidExpiryAndAutoRenew() {
        when(account.isSmartContract()).thenReturn(true);
        when(account.getBalance()).thenReturn(defaultBalance);
        when(store.getAccount(ADDRESS, OnMissing.THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(Instant.MAX.getEpochSecond());
        when(mirrorNodeEvmProperties.shouldAutoRenewContracts()).thenReturn(true);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isNotUsableContractWithPastExpiryAndAutoRenew() {
        when(account.isSmartContract()).thenReturn(true);
        when(account.getBalance()).thenReturn(defaultBalance);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(1L);
        when(mirrorNodeEvmProperties.shouldAutoRenewContracts()).thenReturn(true);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Need to properly test expiry with/without feature flag gating")
    @Test
    void isUsableContractWithPastExpiryAndNoAutoRenew() {
        when(account.getBalance()).thenReturn(defaultBalance);
        when(account.isSmartContract()).thenReturn(true);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(mirrorNodeEvmProperties.shouldAutoRenewContracts()).thenReturn(false);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void getBalance() {
        final long balance = 23L;
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isEqualTo(balance);
    }

    @Test
    void getBalanceForAccountWithEmptyOne() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isZero();
    }

    @Test
    void isExtant() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        final var result = mirrorEntityAccess.isExtant(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isExtantForZeroAddress() {
        when(store.getAccount(ZERO, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isExtant(ZERO);
        assertThat(result).isFalse();
    }

    @Test
    void isTokenAccount() {
        when(store.getToken(ADDRESS, OnMissing.DONT_THROW)).thenReturn(token);
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isATokenAccountForMissingEntity() {
        when(store.getToken(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void getAlias() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getAlias()).thenReturn(ByteString.copyFrom(DATA));
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isNotEqualTo(EMPTY);
    }

    @Test
    void getAliasForAccountWithEmptyOne() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isEqualTo(EMPTY);
    }

    @Test
    void getStorage() {
        when(contractStateRepository.findStorage(ENTITY_ID, BYTES.toArrayUnsafe()))
                .thenReturn(Optional.of(DATA));
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ADDRESS, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(HEX));
    }

    @Test
    void getStorageFailsForNonMirrorAddress() {
        when(store.getAccount(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        when(store.getToken(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        final var key = Bytes.fromHexString(NON_MIRROR_ADDRESS.toHexString());
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(NON_MIRROR_ADDRESS, key));
        assertThat(result).isEqualTo(UInt256.fromHexString(ZERO.toHexString()));
    }

    @Test
    void getStorageFailsForZeroAddress() {
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ZERO, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(ZERO.toHexString()));
    }

    @Test
    void fetchCodeIfPresent() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentForNonMirrorEvm() {
        when(store.getAccount(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getEntityId()).thenReturn(ENTITY_ID);
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(NON_MIRROR_ADDRESS);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentReturnsEmpty() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.empty());
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isEqualTo(Bytes.EMPTY);
    }
}
