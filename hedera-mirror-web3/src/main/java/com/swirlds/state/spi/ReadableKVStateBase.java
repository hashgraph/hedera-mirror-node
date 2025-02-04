/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import com.hedera.mirror.web3.common.ContractCallContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A base class for implementations of {@link ReadableKVState} and {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unchecked")
public abstract class ReadableKVStateBase<K, V> implements ReadableKVState<K, V> {
    /** The state key, which cannot be null */
    private final String stateKey;

    private final Set<K> unmodifiableReadKeys = new HashSet<>();

    private static final Object marker = new Object();

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    protected ReadableKVStateBase(@Nonnull String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    /** {@inheritDoc} */
    @Override
    @Nonnull
    public final String getStateKey() {
        return stateKey;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public V get(@Nonnull K key) {
        // We need to cache the item because somebody may perform business logic basic on this
        // contains call, even if they never need the value itself!
        Objects.requireNonNull(key);
        if (!hasBeenRead(key)) {
            final var value = readFromDataSource(key);
            markRead(key, value);
        }
        final var value = getReadCache().get(key);
        return (value == marker) ? null : (V) value;
    }

    /**
     * Gets the set of keys that a client read from the {@link ReadableKVState}.
     *
     * @return The possibly empty set of keys.
     */
    @Nonnull
    public final Set<K> readKeys() {
        return (Set<K>) getReadCache().entrySet();
    }

    /** {@inheritDoc} */
    @Nonnull
    @Override
    public Iterator<K> keys() {
        return iterateFromDataSource();
    }

    /** Clears all cached data, including the set of all read keys. */
    /*@OverrideMustCallSuper*/
    public void reset() {
        getReadCache().clear();
    }

    /**
     * Reads the keys from the underlying data source (which may be a merkle data structure, a
     * fast-copyable data structure, or something else).
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V readFromDataSource(@Nonnull K key);

    /**
     * Gets an iterator from the data source that iterates over all keys.
     *
     * @return An iterator over all keys in the data source.
     */
    @Nonnull
    protected abstract Iterator<K> iterateFromDataSource();

    /**
     * Records the given key and associated value were read. {@link WritableKVStateBase} will call
     * this method in some cases when a key is read as part of a modification (for example, with
     * {@link WritableKVStateBase#getForModify}).
     *
     * @param key The key
     * @param value The value
     */
    protected final void markRead(@Nonnull K key, @Nullable V value) {
        getReadCache().put(key, Objects.requireNonNullElseGet(value, () -> (V) marker));
    }

    /**
     * Gets whether this key has been read at some point by this {@link ReadableKVStateBase}.
     *
     * @param key The key.
     * @return Whether it has been read
     */
    protected final boolean hasBeenRead(@Nonnull K key) {
        return getReadCache().containsKey(key);
    }

    private Map<Object, Object> getReadCache() {
        return ContractCallContext.get().getReadCacheState(getStateKey());
    }
}
