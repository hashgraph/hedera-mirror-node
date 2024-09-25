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
import com.swirlds.state.spi.ReadableKVStateBase;
import java.util.Collections;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;

public class ContractStorageDatabaseReadableKVState extends ReadableKVStateBase<SlotKey, SlotValue> {

    private static final String KEY = "STORAGE";
    private final ContractStateRepository contractStateRepository;

    protected ContractStorageDatabaseReadableKVState(final ContractStateRepository contractStateRepository) {
        super(KEY);
        this.contractStateRepository = contractStateRepository;
    }

    @Override
    protected SlotValue readFromDataSource(@NotNull SlotKey key) {
        if (!key.hasContractID()) {
            return SlotValue.DEFAULT;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        return timestamp
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(
                        key.contractID().contractNum(), key.key().toByteArray(), t))
                .orElse(contractStateRepository.findStorage(
                        key.contractID().contractNum(), key.key().toByteArray()))
                .map(byteArr -> new SlotValue(Bytes.wrap(byteArr), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(SlotValue.DEFAULT);
    }

    @NotNull
    @Override
    protected Iterator<SlotKey> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
