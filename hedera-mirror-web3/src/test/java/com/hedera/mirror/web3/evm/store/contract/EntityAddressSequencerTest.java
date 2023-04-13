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

package com.hedera.mirror.web3.evm.store.contract;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityAddressSequencerTest {
    private static final long MAX_ID = 123L;

    @Mock
    EntityRepository entityRepository;

    @InjectMocks
    private EntityAddressSequencer entityAddressSequencer;

    @BeforeEach
    void setup() {
        when(entityRepository.findMaxId()).thenReturn(MAX_ID);
    }

    @Test
    void getNewContractId() {
        final long shardNum = 1L;
        final long realmNum = 2L;

        final Address sponsor = new Id(shardNum, realmNum, 111L).asEvmAddress();

        assertThat(entityAddressSequencer.getNewContractId(sponsor))
                .returns(shardNum, ContractID::getShardNum)
                .returns(realmNum, ContractID::getRealmNum)
                .returns(MAX_ID + 1, ContractID::getContractNum);
    }

    @Test
    void getNextEntityIdReturnsNextId() {
        final var actual = entityAddressSequencer.getNextEntityId();

        assertThat(actual).isEqualTo(MAX_ID + 1);
    }

    @Test
    void getNextEntityIdGetsRepositoryValueOnlyOnce() {
        entityAddressSequencer.getNextEntityId();
        entityAddressSequencer.getNextEntityId();
        final var actual = entityAddressSequencer.getNextEntityId();

        assertThat(actual).isEqualTo(MAX_ID + 3);
        verify(entityRepository, times(1)).findMaxId();
    }
}
