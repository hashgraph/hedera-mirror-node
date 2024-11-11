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

import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MapWritableStates extends AbstractMapReadableState implements WritableStates, CommittableWritableStates {

    @Nullable
    private final Runnable onCommit;

    public MapWritableStates(@Nonnull final Map<String, ?> states) {
        this(states, null);
    }

    public MapWritableStates(@Nonnull final Map<String, ?> states, @Nullable final Runnable onCommit) {
        super(states);
        this.onCommit = onCommit;
    }

    @Nonnull
    @Override
    public <K, V> WritableKVState<K, V> get(@Nonnull String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state key: " + stateKey);
        }
        if (!(state instanceof WritableKVState)) {
            throw new IllegalArgumentException("State is not an instance of WritableKVState: " + stateKey);
        }

        return (WritableKVState<K, V>) state;
    }

    @Nonnull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@Nonnull final String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state key: " + stateKey);
        }

        if (!(state instanceof WritableSingletonState)) {
            throw new IllegalArgumentException("State is not an instance of WritableSingletonState: " + stateKey);
        }

        return (WritableSingletonState<T>) state;
    }

    @Nonnull
    @Override
    public <E> WritableQueueState<E> getQueue(@Nonnull final String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state key: " + stateKey);
        }

        if (!(state instanceof WritableQueueState)) {
            throw new IllegalArgumentException("State is not an instance of WritableQueueState: " + stateKey);
        }

        return (WritableQueueState<E>) state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapWritableStates that = (MapWritableStates) o;
        return Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(states);
    }

    @Override
    public void commit() {
        states.values().forEach(state -> {
            switch (state) {
                case WritableKVStateBase kv -> kv.commit();
                case WritableSingletonStateBase singleton -> singleton.commit();
                case WritableQueueStateBase queue -> queue.commit();
                default -> throw new IllegalStateException(
                        "Unknown state type " + state.getClass().getName());
            }
        });
        if (onCommit != null) {
            onCommit.run();
        }
    }

    @Override
    public String toString() {
        return "MapWritableStates{" + "states=" + states + '}';
    }
}
