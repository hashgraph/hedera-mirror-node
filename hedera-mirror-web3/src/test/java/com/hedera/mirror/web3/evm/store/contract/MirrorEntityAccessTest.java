package com.hedera.mirror.web3.evm.store.contract;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.EMPTY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.mirror.web3.repository.EntityRepository;

@ExtendWith(MockitoExtension.class)
class MirrorEntityAccessTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Bytes BYTES = Bytes.fromHexString(HEX);
    private static final byte[] DATA = BYTES.toArrayUnsafe();
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final EntityId ENTITY = DomainUtils.fromEvmAddress(ADDRESS.toArrayUnsafe());
    private static final Long ENTITY_ID = EntityIdEndec.encode(ENTITY.getShardNum(), ENTITY.getRealmNum(),
            ENTITY.getEntityNum());
    @Mock
    private EntityRepository entityRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractStateRepository contractStateRepository;
    @Mock
    Entity entity;

    @InjectMocks
    private MirrorEntityAccess mirrorEntityAccess;

    @Test
    void isUsableWithPositiveBalance() {
        final long balance = 23L;
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isUsableWithNegativeBalance() {
        final long balance = -1L;
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void isUsableWithAliasNegative() {
        final var address = Address.fromHexString("0x3232134567785444e");
        final var result = mirrorEntityAccess.isUsable(address);
        assertThat(result).isFalse();
    }

    @Test
    void getBalance() {
        final long balance = 23L;
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isEqualTo(balance);
    }

    @Test
    void getBalanceForAccountWithEmptyOne() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isZero();
    }

    @Test
    void isExtant() {
        when(entityRepository.existsById(ENTITY_ID)).thenReturn(true);
        final var result = mirrorEntityAccess.isExtant(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isTokenAccount() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isNotATokenAccount() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.ACCOUNT);
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void isATokenAccountForMissingEntity() {
        final var address = Address.fromHexString("0x3232134567785444e");
        final var result = mirrorEntityAccess.isTokenAccount(address);
        assertThat(result).isFalse();
    }

    @Test
    void getAlias() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getAlias()).thenReturn(DATA);
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isNotEqualTo(EMPTY);
    }

    @Test
    void getAliasForAccountWithEmptyOne() {
        when(entityRepository.findByIdAndDeletedIsFalse(ENTITY_ID)).thenReturn(Optional.of(entity));
        when(entity.getAlias()).thenReturn(new byte[] {});
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isEqualTo(EMPTY);
    }

    @Test
    void getStorage() {
        when(contractStateRepository.findStorage(ENTITY_ID, BYTES.toArrayUnsafe())).thenReturn(Optional.of(DATA));
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ADDRESS, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(HEX));
    }

    @Test
    void fetchCodeIfPresent() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentForNonMirrorEvm() {
        final var address = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        when(mirrorEntityAccess.findEntity(address)).thenReturn(Optional.of(entity));
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(address);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentReturnsEmpy() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.empty());
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isEqualTo(Bytes.EMPTY);
    }
}
