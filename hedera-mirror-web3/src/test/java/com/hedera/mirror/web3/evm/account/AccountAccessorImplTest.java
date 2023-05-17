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

package com.hedera.mirror.web3.evm.account;

import static com.google.protobuf.ByteString.EMPTY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.store.contracts.MirrorEntityAccess;
import com.hedera.mirror.web3.repository.EntityRepository;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountAccessorImplTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final String ALIAS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ALIAS_ADDRESS = Address.fromHexString(ALIAS_HEX);
    private static final Bytes BYTES = Bytes.fromHexString(HEX);
    private static final byte[] DATA = BYTES.toArrayUnsafe();

    @Mock
    private MirrorEntityAccess mirrorEntityAccess;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private Entity account;

    @InjectMocks
    public AccountAccessorImpl accountAccessor;

    @Test
    void isTokenAddressTrue() {
        when(mirrorEntityAccess.isTokenAccount(ADDRESS)).thenReturn(true);
        final var result = accountAccessor.isTokenAddress(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isTokenAddressFalse() {
        when(mirrorEntityAccess.isTokenAccount(ADDRESS)).thenReturn(false);
        final var result = accountAccessor.isTokenAddress(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void canonicalAddress() {
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS);
    }

    @Test
    void canonicalAliasAddress() {
        when(entityRepository.findByEvmAddressAndDeletedIsFalse(ALIAS_ADDRESS.toArray()))
                .thenReturn(Optional.of(account));
        final var result = accountAccessor.canonicalAddress(ALIAS_ADDRESS);
        assertThat(result).isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void missingCanonicalAliasAddressResolvesToItself() {
        final var result = accountAccessor.canonicalAddress(ALIAS_ADDRESS);
        assertThat(result).isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void isExtantTrue() {
        when(mirrorEntityAccess.isExtant(ADDRESS)).thenReturn(true);
        when(mirrorEntityAccess.alias(ADDRESS)).thenReturn(EMPTY);
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS);
    }

    @Test
    void alias() {
        when(mirrorEntityAccess.isExtant(ADDRESS)).thenReturn(true);
        when(mirrorEntityAccess.alias(ADDRESS)).thenReturn(ByteString.copyFrom(DATA));
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS);
    }

    @Test
    void aliasDifferentFromEvmAddressSize() {
        when(mirrorEntityAccess.isExtant(ADDRESS)).thenReturn(true);
        when(mirrorEntityAccess.alias(ADDRESS)).thenReturn(ByteString.copyFrom(new byte[32]));
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS);
    }
}
