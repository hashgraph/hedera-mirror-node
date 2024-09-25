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

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class ContractBytecodeReadableKVState extends ReadableKVStateBase<ContractID, Bytecode> {

    private static final String KEY = "BYTECODE";

    private final CommonEntityAccessor commonEntityAccessor;
    private final ContractRepository contractRepository;

    protected ContractBytecodeReadableKVState(
            final CommonEntityAccessor commonEntityAccessor, final ContractRepository contractRepository) {
        super(KEY);
        this.commonEntityAccessor = commonEntityAccessor;
        this.contractRepository = contractRepository;
    }

    @Override
    protected Bytecode readFromDataSource(@NotNull ContractID contractID) {
        final var contractEntity = commonEntityAccessor.get(contractID, Optional.empty());
        if (contractEntity.isEmpty()) {
            return null;
        }

        final var runtimeCode =
                contractRepository.findRuntimeBytecode(contractEntity.get().getId());
        final var runtimeBytes = runtimeCode.map(Bytes::wrap).orElse(null);
        return runtimeBytes == null ? null : new Bytecode(runtimeBytes);
    }

    @NotNull
    @Override
    protected Iterator<ContractID> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
