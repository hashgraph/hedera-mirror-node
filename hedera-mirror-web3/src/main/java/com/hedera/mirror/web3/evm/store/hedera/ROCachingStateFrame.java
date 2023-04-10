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

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and disallows updates/deletes. */
@SuppressWarnings(
        "java:S1192") // "define a constant instead of duplicating this literal" - worse readability if applied to small
// literals
public class ROCachingStateFrame<K> extends CachingStateFrame<K> {

    public ROCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    @NonNull
    public Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key");

        final var entry = cache.get(key);
        return switch (entry.state()) {
            case NOT_YET_FETCHED -> upstreamFrame.flatMap(upstreamFrame -> {
                final var upstreamAccessor = upstreamFrame.getAccessor(klass);
                final var upstreamValue = upstreamAccessor.get(key);
                cache.fill(key, upstreamValue.orElse(null));
                return upstreamValue;
            });
            case PRESENT, UPDATED -> Optional.of(entry.value());
            case MISSING, DELETED -> Optional.empty();
            case INVALID -> throw new IllegalArgumentException("trying to get value when state is invalid");
        };
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key", value, "value");
        throw new UnsupportedOperationException("cannot write value to a R/O cache");
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        requireAllNonNull(klass, "klass", cache, "cache", key, "key");
        throw new UnsupportedOperationException("cannot delete value from a R/O cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a R/O cache");
    }
}
