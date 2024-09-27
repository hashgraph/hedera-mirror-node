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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Iterator;

public class AliasesReadableKVState extends ReadableKVStateBase<ProtoBytes, AccountID> {

    private static final String KEY = "ALIASES";

    private final CommonEntityAccessor commonEntityAccessor;

    protected AliasesReadableKVState(final CommonEntityAccessor commonEntityAccessor) {
        super(KEY);
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected AccountID readFromDataSource(@NotNull ProtoBytes evmAddress) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                evmAddress.value().toByteArray(), timestamp);
        return entity.map(value -> AccountID.newBuilder()
                        .shardNum(value.getShard())
                        .realmNum(value.getRealm())
                        .alias(Bytes.wrap(value.getAlias()))
                        .build())
                .orElse(null);
    }

    @NotNull
    @Override
    protected Iterator<ProtoBytes> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
