/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEvmContractAliasesTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final String HEX2 = "0x00000000000000000000000000000000000004e5";
    private static final Address ADDRESS2 = Address.fromHexString(HEX2);
    private static final Address ALIAS = Address.fromHexString("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");
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
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddress() {
        Address alias = ALIAS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.aliases.put(alias, address);

        assertThat(mirrorEvmContractAliases.resolveForEvm(alias)).isEqualTo(address);
    }

    @Test
    void resolveForEvmForContractShouldReturnFromPendingChangesRightAfterLink() {
        Address alias = ALIAS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.link(alias, address);

        final var result = mirrorEvmContractAliases.resolveForEvm(ALIAS);
        assertThat(result).isEqualTo(ADDRESS2);
    }

    @Test
    void initializeWithEmptyAliasesMap() {
        assertThat(mirrorEvmContractAliases.aliases).isNotNull().isEmpty();
    }

    @Test
    void link() {
        mirrorEvmContractAliases.pendingChanges.clear();

        Address alias = ALIAS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.link(alias, address);

        assertThat(mirrorEvmContractAliases.pendingChanges).hasSize(1).hasEntrySatisfying(alias, v -> assertThat(v)
                .isEqualTo(address));
    }

    @Test
    void unlink() {
        mirrorEvmContractAliases.pendingChanges.clear();
        mirrorEvmContractAliases.pendingChanges.put(ADDRESS, ADDRESS2);
        mirrorEvmContractAliases.unlink(ADDRESS);
        assertThat(mirrorEvmContractAliases.aliases).isEmpty();
    }
}
