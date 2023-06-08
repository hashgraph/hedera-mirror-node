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

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
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

    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);

    private static final EntityId entityId = new EntityId(0L, 0L, 3L, EntityType.TOKEN);

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
    void resolveForEvmShouldReturnInputWhenItIsMirrorAddress() {
        assertThat(mirrorEvmContractAliases.resolveForEvm(ADDRESS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddress() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmForContractWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(mirrorEntityAccess.findEntity(ALIAS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.CONTRACT);
        when(entity.toEntityId()).thenReturn(entityId);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @Test
    void resolveForEvmForTokenWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(mirrorEntityAccess.findEntity(ALIAS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(entity.toEntityId()).thenReturn(entityId);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @Test
    void resolveForEvmForContractShouldReturnFromPendingChangesRightAfterLink() {
        mirrorEvmContractAliases.link(ALIAS, ADDRESS);

        final var result = mirrorEvmContractAliases.resolveForEvm(ALIAS);
        assertThat(result).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenTypeIsNotTokenOrContractShouldFail() {
        when(mirrorEntityAccess.findEntity(ALIAS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOPIC);

        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(ALIAS))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("Not a contract or token: " + ALIAS_HEX);
    }

    @Test
    void resolveForEvmWhenInvalidAddressShouldFail() {
        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(ALIAS))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No such contract or token: " + ALIAS_HEX);
    }

    @Test
    void initializeWithEmptyAliasesMap() {
        assertThat(mirrorEvmContractAliases.aliases).isNotNull().isEmpty();
    }

    @Test
    void link() {
        mirrorEvmContractAliases.pendingChanges.clear();

        mirrorEvmContractAliases.link(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.pendingChanges).hasSize(1).hasEntrySatisfying(ALIAS, v -> assertThat(v)
                .isEqualTo(ADDRESS));
    }

    @Test
    void unlink() {
        mirrorEvmContractAliases.pendingChanges.clear();
        mirrorEvmContractAliases.pendingChanges.put(ALIAS, ADDRESS);
        mirrorEvmContractAliases.unlink(ALIAS);
        assertThat(mirrorEvmContractAliases.aliases).isEmpty();
    }
}
