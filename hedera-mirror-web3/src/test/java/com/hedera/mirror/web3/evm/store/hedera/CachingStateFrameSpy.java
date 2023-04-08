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

public class CachingStateFrameSpy<K> extends CachingStateFrame<K> {

    protected CachingStateFrameSpy(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
        if (upstreamFrame.isEmpty())
            throw new IllegalArgumentException("upstream frame of SpyingStateFrame must not be null");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> downstreamFrame) {
        Objects.requireNonNull(downstreamFrame, "downstreamFrame");
        upstreamFrame.orElseThrow().updatesFromDownstream(downstreamFrame);
    }

    @NonNull
    @Override
    protected Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key");
        return upstreamFrame.orElseThrow().getValue(klass, cache, key);
    }

    @Override
    protected void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key", value, "value");
        upstreamFrame.orElseThrow().setValue(klass, cache, key, value);
    }

    @Override
    protected void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key");
        upstreamFrame.orElseThrow().deleteValue(klass, cache, key);
    }
}
