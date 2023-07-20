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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityAddressSequencerTest {
    private static final long contractNum = 1_000_000_000L;
    private static final Address sponsor = new Id(0, 0, contractNum).asEvmAddress();

    @InjectMocks
    private EntityAddressSequencer entityAddressSequencer;

    @Test
    void getNewContractId() {
        assertThat(entityAddressSequencer.getNewContractId(sponsor))
                .returns(0L, ContractID::getShardNum)
                .returns(0L, ContractID::getRealmNum)
                .returns(contractNum, ContractID::getContractNum);
    }

    @Test
    void getNewAccountId() {
        assertThat(entityAddressSequencer.getNewAccountId())
                .returns(0L, AccountID::getRealmNum)
                .returns(0L, AccountID::getShardNum)
                .returns(1000000000L, AccountID::getAccountNum);
        assertThat(entityAddressSequencer.getNewAccountId())
                .returns(0L, AccountID::getRealmNum)
                .returns(0L, AccountID::getShardNum)
                .returns(1000000001L, AccountID::getAccountNum);
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
