/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.hederahashgraph.api.proto.java.ContractID;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class EntityAddressSequencer {
    private final AtomicLong entityIds = new AtomicLong(1_000_000_000);

    public ContractID getNewContractId(Address sponsor) {
        final var newContractSponsor = accountIdFromEvmAddress(sponsor.toArrayUnsafe());
        return ContractID.newBuilder()
                .setRealmNum(newContractSponsor.getRealmNum())
                .setShardNum(newContractSponsor.getShardNum())
                .setContractNum(getNextEntityId())
                .build();
    }

    private long getNextEntityId() {
        return entityIds.getAndIncrement();
    }
}
