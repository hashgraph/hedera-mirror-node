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
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

public class MapWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> readableKVState;

    public MapWritableKVState(@Nonnull String stateKey, @Nonnull ReadableKVState<K, V> readableKVState) {
        super(stateKey);
        this.readableKVState = readableKVState;
    }

    // The readable state's values are immutable, hence callers would not be able
    // to modify the readable state's objects.
    @Override
    protected V getForModifyFromDataSource(@Nonnull K key) {
        return readableKVState.get(key);
    }

    @Override
    protected void putIntoDataSource(@Nonnull K key, @Nonnull V value) {
        put(key, value); // put only in memory
    }

    @Override
    protected void removeFromDataSource(@Nonnull K key) {
        remove(key); // remove only in memory
    }

    @Override
    protected long sizeOfDataSource() {
        return readableKVState.size();
    }

    @Override
    protected V readFromDataSource(@Nonnull K key) {
        return readableKVState.get(key);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public void commit() {
        reset();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapWritableKVState<?, ?> that = (MapWritableKVState<?, ?>) o;
        return Objects.equals(getStateKey(), that.getStateKey())
                && Objects.equals(readableKVState, that.readableKVState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateKey(), readableKVState);
    }
}
