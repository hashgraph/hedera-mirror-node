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

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;

@Named
public class ContractStorageReadableKVState extends ReadableKVStateBase<SlotKey, SlotValue> {

    private final ContractStateRepository contractStateRepository;

    protected ContractStorageReadableKVState(final ContractStateRepository contractStateRepository) {
        super("STORAGE");
        this.contractStateRepository = contractStateRepository;
    }

    @Nullable
    @Override
    public SlotValue get(@Nonnull SlotKey key) {
        return readFromDataSource(key);
    }

    @Override
    protected SlotValue readFromDataSource(@Nonnull SlotKey slotKey) {
        if (!slotKey.hasContractID()) {
            return null;
        }

        final var timestamp = ContractCallContext.getTimestamp();
        final var contractID = slotKey.contractID();
        final var entityId = EntityIdUtils.entityIdFromContractId(contractID).getId();
        final var keyBytes = slotKey.key().toByteArray();
        return timestamp
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(entityId, keyBytes, t))
                .orElse(contractStateRepository.findStorage(entityId, keyBytes))
                .map(byteArr -> new SlotValue(Bytes.wrap(byteArr), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }

    @Nonnull
    @Override
    protected Iterator<SlotKey> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
