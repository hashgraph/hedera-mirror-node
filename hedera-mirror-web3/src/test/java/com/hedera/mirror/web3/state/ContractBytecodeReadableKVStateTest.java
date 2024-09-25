/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractBytecodeReadableKVStateTest {

    private static final ContractID CONTRACT_ID =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.CONTRACT_NUM, 1L));
    private static final EntityId ENTITY_ID =
            EntityId.of(CONTRACT_ID.shardNum(), CONTRACT_ID.realmNum(), CONTRACT_ID.contractNum());
    private static final Bytes BYTES = Bytes.fromBase64("123456");
    private static final Bytecode BYTECODE = new Bytecode(BYTES);

    @InjectMocks
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private ContractRepository contractRepository;

    @Test
    void whenContractEntityIsNullReturnNull() {
        when(commonEntityAccessor.get(CONTRACT_ID, Optional.empty())).thenReturn(Optional.empty());
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenContractEntityExistsReturnRuntimeBytecode() {
        when(commonEntityAccessor.get(CONTRACT_ID, Optional.empty()))
                .thenReturn(Optional.of(Entity.builder().id(ENTITY_ID.getId()).build()));
        when(contractRepository.findRuntimeBytecode(ENTITY_ID.getId())).thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractRuntimeBytecodeIsNullReturnNull() {
        when(commonEntityAccessor.get(CONTRACT_ID, Optional.empty()))
                .thenReturn(Optional.of(Entity.builder().id(ENTITY_ID.getId()).build()));
        when(contractRepository.findRuntimeBytecode(ENTITY_ID.getId())).thenReturn(Optional.empty());
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID))
                .satisfies(bytecode -> assertThat(bytecode).isNull());
    }
}
