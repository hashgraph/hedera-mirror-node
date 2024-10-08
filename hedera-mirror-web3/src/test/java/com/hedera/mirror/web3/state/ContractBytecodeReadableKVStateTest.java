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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractBytecodeReadableKVStateTest {

    private static final ContractID CONTRACT_ID_WITH_NUM =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.CONTRACT_NUM, 1L));
    private static final EntityId ENTITY_ID_WITH_NUM = EntityId.of(
            CONTRACT_ID_WITH_NUM.shardNum(), CONTRACT_ID_WITH_NUM.realmNum(), CONTRACT_ID_WITH_NUM.contractNum());
    private static final Bytes BYTES = Bytes.fromBase64("123456");
    private static final Bytecode BYTECODE = new Bytecode(BYTES);
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final ContractID CONTRACT_ID_WITH_EVM_ADDRESS =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.EVM_ADDRESS, Bytes.wrap(ADDRESS.toArrayUnsafe())));
    private static final EntityId ENTITY_ID_WITH_EVM_ADDRESS = EntityId.of(entityIdNumFromEvmAddress(ADDRESS));

    @InjectMocks
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private ContractRepository contractRepository;

    @Test
    void whenContractIdAndEvmAddressAreNotSetReturnNull() {
        assertThat(contractBytecodeReadableKVState.get(
                        new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.UNSET, null))))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenContractNumIsSetReturnRuntimeBytecode() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_NUM.getId()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_NUM))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractEvmAddressIsSetReturnRuntimeBytecode() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_EVM_ADDRESS.getId()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_EVM_ADDRESS))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractRuntimeBytecodeIsNullReturnNull() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_NUM.getId())).thenReturn(Optional.empty());
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_NUM))
                .satisfies(bytecode -> assertThat(bytecode).isNull());
    }

    @Test
    void getExpectedSize() {
        assertThat(contractBytecodeReadableKVState.size()).isEqualTo(0L);
    }

    @Test
    void iterateFromDataSourceReturnsEmptyIterator() {
        assertThat(contractBytecodeReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }
}
