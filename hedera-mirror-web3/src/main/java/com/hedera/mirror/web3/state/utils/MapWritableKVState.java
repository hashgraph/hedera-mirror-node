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

package com.hedera.mirror.web3.state.utils;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class MapWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final Map<K, V> backingStore;
    private final ReadableKVState<K, V> readableBackingStore;

    /**
     * Create an instance using the given map as the backing store. This is useful when you want to
     * pre-populate the map, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    //    public MapWritableKVState(@Nonnull final String stateKey, @Nonnull final Map<K, V> backingStore) {
    //        super(stateKey);
    //        this.backingStore = Objects.requireNonNull(backingStore);
    //    }

    public MapWritableKVState(
            @Nonnull final String stateKey, @Nonnull final ReadableKVState<K, V> readableBackingStore) {
        super(stateKey);
        this.backingStore = new HashMap<>();
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
    }

    @Override
    protected V readFromDataSource(@Nonnull K key) {
        //        return backingStore.get(key);
        return readableBackingStore.get(key);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return readableBackingStore.keys();
    }

    @Override
    protected V getForModifyFromDataSource(@Nonnull K key) {
        return readableBackingStore.get(key);
    }

    @Override
    protected void putIntoDataSource(@Nonnull K key, @Nonnull V value) {
        backingStore.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@Nonnull K key) {
        backingStore.remove(key);
    }

    @Override
    public long sizeOfDataSource() {
        return backingStore.size();
    }

    @Override
    public String toString() {
        return "MapWritableKVState{" + "backingStore=" + backingStore + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapWritableKVState<?, ?> that = (MapWritableKVState<?, ?>) o;
        return Objects.equals(getStateKey(), that.getStateKey()) && Objects.equals(backingStore, that.backingStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateKey(), backingStore);
    }
}
