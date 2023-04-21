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

package com.hedera.mirror.web3.evm.store.contract;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityAddressSequencerTest {
    private static final long contractNum = 1_000_000_000L;
    private static final long shardNum = 1L;
    private static final long realmNum = 2L;
    private static final Address sponsor = new Id(shardNum, realmNum, contractNum).asEvmAddress();

    @InjectMocks
    private EntityAddressSequencer entityAddressSequencer;

    @Test
    void getNewContractId() {
        assertThat(entityAddressSequencer.getNewContractId(sponsor))
                .returns(shardNum, ContractID::getShardNum)
                .returns(realmNum, ContractID::getRealmNum)
                .returns(contractNum, ContractID::getContractNum);
    }

    @Test
    void getNextEntityIdReturnsNextId() {
        final var actualAddress = entityAddressSequencer.getNewContractId(sponsor);
        assertThat(actualAddress.getContractNum()).isEqualTo(contractNum);
    }

    @Test
    void getNextEntityIdWorksCorrectlyAfterMultipleCalls() {
        entityAddressSequencer.getNewContractId(sponsor);
        entityAddressSequencer.getNewContractId(sponsor);
        final var actual = entityAddressSequencer.getNewContractId(sponsor);

        assertThat(actual.getContractNum()).isEqualTo(contractNum + 2);
    }
}
