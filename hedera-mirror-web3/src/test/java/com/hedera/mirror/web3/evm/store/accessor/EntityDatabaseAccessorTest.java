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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.repository.EntityRepository;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final String ALIAS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ALIAS_ADDRESS = Address.fromHexString(ALIAS_HEX);

    private static final Entity entity = mock(Entity.class);

    @InjectMocks
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Test
    void getEntityByAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(entity));

        entityDatabaseAccessor.get(ADDRESS);

        verify(entityRepository).findByIdAndDeletedIsFalse(entityIdNumFromEvmAddress(ADDRESS));
    }

    @Test
    void getEntityByAlias() {
        entityDatabaseAccessor.get(ALIAS_ADDRESS);

        verify(entityRepository).findByEvmAddressAndDeletedIsFalse(ALIAS_ADDRESS.toArrayUnsafe());
    }
}
