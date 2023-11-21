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
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.EntityRepository;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final String ALIAS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ALIAS_ADDRESS = Address.fromHexString(ALIAS_HEX);

    private static final Entity mockEntity = mock(Entity.class);

    @InjectMocks
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private EntityRepository entityRepository;

    private MockedStatic<ContractCallContext> staticMock;

    @Mock
    private ContractCallContext contractCallContext;

    @BeforeEach
    void setup() {
        staticMock = mockStatic(ContractCallContext.class);
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @AfterEach
    void clean() {
        staticMock.close();
    }

    @Test
    void getEntityByAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(entityIdNumFromEvmAddress(ADDRESS)))
                .thenReturn(Optional.of(mockEntity));

        assertThat(entityDatabaseAccessor.get(ADDRESS))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAddressHistorical() {
        final var recordFile = new RecordFile();
        recordFile.setConsensusEnd(123L);
        when(contractCallContext.getRecordFile()).thenReturn(recordFile);
        when(entityRepository.findActiveByIdAndTimestamp(entityIdNumFromEvmAddress(ADDRESS), 123L))
                .thenReturn(Optional.of(mockEntity));

        assertThat(entityDatabaseAccessor.get(ADDRESS))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAlias() {
        when(entityRepository.findByEvmAddressAndDeletedIsFalse(ALIAS_ADDRESS.toArrayUnsafe()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(entityDatabaseAccessor.get(ALIAS_ADDRESS))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAliasHistorical() {
        final var recordFile = new RecordFile();
        recordFile.setConsensusEnd(123L);
        when(contractCallContext.getRecordFile()).thenReturn(recordFile);
        when(entityRepository.findActiveByEvmAddressAndTimestamp(ALIAS_ADDRESS.toArrayUnsafe(), 123L))
                .thenReturn(Optional.of(mockEntity));

        assertThat(entityDatabaseAccessor.get(ALIAS_ADDRESS))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void evmAddressFromIdReturnZeroWhenNoEntityFound() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.empty());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), -1L))
                .isEqualTo(Address.ZERO);
    }

    @Test
    void evmAddressFromIdReturnZeroWhenNoEntityFoundHistorical() {
        when(entityRepository.findActiveByIdAndTimestamp(0L, 123L)).thenReturn(Optional.empty());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), 123L))
                .isEqualTo(Address.ZERO);
    }

    @Test
    void evmAddressFromIdReturnAddressFromEntityEvmAddressWhenPresent() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), -1L))
                .isEqualTo(ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAddressFromEntityEvmAddressWhenPresentHistorical() {
        when(entityRepository.findActiveByIdAndTimestamp(0L, 123L)).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), 123L))
                .isEqualTo(ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAliasFromEntityWhenPresentAndNoEvmAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(ALIAS_ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), -1L))
                .isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAliasFromEntityWhenPresentAndNoEvmAddressHistorical() {
        when(entityRepository.findActiveByIdAndTimestamp(0L, 123L)).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(ALIAS_ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), 123L))
                .isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnToAddressByDefault() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(null);

        final var entityId = EntityId.of(1L, 2L, 3L);
        assertThat(entityDatabaseAccessor.evmAddressFromId(entityId, -1L)).isEqualTo(toAddress(entityId));
    }

    @Test
    void evmAddressFromIdReturnToAddressByDefaultHistorical() {
        final var entityId = EntityId.of(1L, 2L, 3L);
        when(entityRepository.findActiveByIdAndTimestamp(entityId.getId(), 123L))
                .thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(null);

        assertThat(entityDatabaseAccessor.evmAddressFromId(entityId, 123L)).isEqualTo(toAddress(entityId));
    }
}
