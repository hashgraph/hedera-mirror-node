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

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and local updates/deletes. */
public class RWCachingStateFrame<Address> extends ROCachingStateFrame<Address> {

    public RWCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address>> upstreamFrame,
            @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    public void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address", entity, "entity");
        cache.update(address, entity);
    }

    @Override
    public void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        cache.delete(address);
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<Address> downstreamFrame) {
        Objects.requireNonNull(downstreamFrame, "downstreamFrame");
        final var thisCaches = this.getInternalCaches();
        final var downstreamCaches = downstreamFrame.getInternalCaches();
        if (thisCaches.size() != downstreamCaches.size())
            throw new IllegalStateException("this frame and downstream frame have different klasses registered");
        for (final var kv : thisCaches.entrySet()) {
            if (!downstreamCaches.containsKey(kv.getKey()))
                throw new IllegalStateException("this frame and downstream frame have different klasses registered");
            kv.getValue().coalesceFrom(downstreamCaches.get(kv.getKey()));
        }
    }
}
