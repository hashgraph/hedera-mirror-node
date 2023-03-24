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

package com.hedera.mirror.web3.evm.store.hedera;

import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

public class CachingStateFrameSpy<Address> extends CachingStateFrame<Address> {

    protected CachingStateFrameSpy(
            @NonNull final Optional<CachingStateFrame<Address>> upstreamFrame,
            @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
        if (upstreamFrame.isEmpty())
            throw new IllegalArgumentException("upstream frame of SpyingStateFrame must not be null");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<Address> downstreamFrame) {
        Objects.requireNonNull(downstreamFrame, "downstreamFrame");
        upstreamFrame.orElseThrow().updatesFromDownstream(downstreamFrame);
    }

    @NonNull
    @Override
    protected Optional<Object> getEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        return upstreamFrame.orElseThrow().getEntity(klass, cache, address);
    }

    @Override
    protected void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address", entity, "entity");
        upstreamFrame.orElseThrow().setEntity(klass, cache, address, entity);
    }

    @Override
    protected void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        upstreamFrame.orElseThrow().deleteEntity(klass, cache, address);
    }
}
