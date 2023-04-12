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

import java.util.Optional;
import lombok.NonNull;

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and local updates/deletes. */
@SuppressWarnings(
        "java:S1192") // "define a constant instead of duplicating this literal" - worse readability if applied to small
// literals
public class RWCachingStateFrame<K> extends ROCachingStateFrame<K> {

    public RWCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        cache.update(key, value);
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        cache.delete(key);
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> downstreamFrame) {
        final var thisCaches = this.getInternalCaches();
        final var downstreamCaches = downstreamFrame.getInternalCaches();
        if (thisCaches.size() != downstreamCaches.size())
            throw new IllegalStateException("This frame and downstream frame have different klasses registered");
        for (final var kv : thisCaches.entrySet()) {
            if (!downstreamCaches.containsKey(kv.getKey()))
                throw new IllegalStateException("This frame and downstream frame have different klasses registered");
            kv.getValue().coalesceFrom(downstreamCaches.get(kv.getKey()));
        }
    }
}
