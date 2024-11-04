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
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unchecked")
public class MapReadableStates implements ReadableStates {

    private final Map<String, ?> states;

    public MapReadableStates(@Nonnull final Map<String, ?> states) {
        this.states = Objects.requireNonNull(states);
    }

    @Nonnull
    @Override
    public <K, V> ReadableKVState<K, V> get(@Nonnull String stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state key: " + stateKey);
        }
        if (!(state instanceof ReadableKVState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableKVState: " + stateKey);
        }

        return (ReadableKVState<K, V>) state;
    }

    @Nonnull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(@Nonnull String stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state key: " + stateKey);
        }

        if (!(state instanceof ReadableSingletonState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableSingletonState: " + stateKey);
        }

        return (ReadableSingletonState<T>) state;
    }

    @Nonnull
    @Override
    public <E> ReadableQueueState<E> getQueue(@Nonnull String stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state key: " + stateKey);
        }

        if (!(state instanceof ReadableQueueState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableQueueState: " + stateKey);
        }

        return (ReadableQueueState<E>) state;
    }

    @Override
    public boolean contains(@Nonnull String stateKey) {
        return states.containsKey(stateKey);
    }

    @Nonnull
    @Override
    public Set<String> stateKeys() {
        return Collections.unmodifiableSet(states.keySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapReadableStates that = (MapReadableStates) o;
        return Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(states);
    }
}
