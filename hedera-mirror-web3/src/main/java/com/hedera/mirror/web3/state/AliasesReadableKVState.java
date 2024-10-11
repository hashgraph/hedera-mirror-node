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
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;

@Named
public class AliasesReadableKVState extends ReadableKVStateBase<ProtoBytes, AccountID> {

    private final CommonEntityAccessor commonEntityAccessor;

    protected AliasesReadableKVState(final CommonEntityAccessor commonEntityAccessor) {
        super("ALIASES");
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected AccountID readFromDataSource(@Nonnull ProtoBytes alias) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(alias.value(), timestamp);
        return entity.map(e -> AccountID.newBuilder()
                        .shardNum(e.getShard())
                        .realmNum(e.getRealm())
                        .accountNum(e.getNum())
                        .build())
                .orElse(null);
    }

    @Nonnull
    @Override
    protected Iterator<ProtoBytes> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }
}
