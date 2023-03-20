package com.hedera.mirror.web3.evm.account;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.exception.InvalidParametersException;

import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;

@ExtendWith(MockitoExtension.class)
class MirrorEvmContractAliasesTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final String HEX2 = "0x00000000000000000000000000000000000004e5";
    private static final String HEX3 = "0x0000000000000000000000000000000000000003";
    private static final Address ADDRESS2 = Address.fromHexString(HEX2);
    private static final Address ADDRESS3 = Address.fromHexString(HEX3);
    private static final String INVALID_HEX_ADDRESS = "0x000000000000000000000000004e5";
    private static final EntityId entityId = new EntityId(0L, 0L, 3L, EntityType.TOKEN);
    private static final Address INVALID_ADDRESS = Address.fromHexString(INVALID_HEX_ADDRESS);

    @Mock
    private MirrorEntityAccess mirrorEntityAccess;
    @Mock
    private Entity entity;

    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @BeforeEach
    void setup() {
        mirrorEvmContractAliases = new MirrorEvmContractAliases(mirrorEntityAccess);
    }

    @Test
    void resolveForEvmSuccess() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.CONTRACT);
        when(entity.getEvmAddress()).thenReturn(ADDRESS2.toArray());
        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS2);
    }

    @Test
    void resolveForEvmTokenSuccess() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(entity.toEntityId()).thenReturn(entityId);
        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS3);
    }

    @Test
    void resolveForEvmContractWithoutEvmAddressSuccess() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.CONTRACT);
        when(entity.toEntityId()).thenReturn(entityId);
        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS3);
    }

    @Test
    void resolveForEvmFailDifferentType() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOPIC);
        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(ADDRESS))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("No such contract or token");
    }

    @Test
    void resolveForEvmFail() {
        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(INVALID_ADDRESS))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("No such contract or token");
    }
}
