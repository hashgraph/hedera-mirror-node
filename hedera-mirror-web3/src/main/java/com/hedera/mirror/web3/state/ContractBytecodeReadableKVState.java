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
import static com.hedera.mirror.web3.state.Utils.isMirror;
import static com.hedera.services.utils.EntityIdUtils.toAddress;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

@Named
public class ContractBytecodeReadableKVState extends ReadableKVStateBase<ContractID, Bytecode> {

    private final ContractRepository contractRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    protected ContractBytecodeReadableKVState(
            final ContractRepository contractRepository, CommonEntityAccessor commonEntityAccessor) {
        super("BYTECODE");
        this.contractRepository = contractRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected Bytecode readFromDataSource(@Nonnull ContractID contractID) {
        final var entityId = toEntityId(contractID);

        return contractRepository
                .findRuntimeBytecode(entityId.getId())
                .map(Bytes::wrap)
                .map(Bytecode::new)
                .orElse(null);
    }

    @Nonnull
    @Override
    protected Iterator<ContractID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private EntityId toEntityId(@Nonnull final com.hedera.hapi.node.base.ContractID contractID) {
        if (contractID.hasContractNum()) {
            return EntityId.of(contractID.shardNum(), contractID.realmNum(), contractID.contractNum());
        } else if (contractID.hasEvmAddress()) {
            final var evmAddress = toAddress(contractID.evmAddress());
            if (isMirror(evmAddress)) {
                return EntityId.of(entityIdNumFromEvmAddress(evmAddress));
            } else {
                return commonEntityAccessor
                        .getEntityByEvmAddressAndTimestamp(
                                contractID.evmAddress().toByteArray(), Optional.empty())
                        .get()
                        .toEntityId();
            }
        }
        return EntityId.EMPTY;
    }
}