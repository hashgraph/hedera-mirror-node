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

package com.hedera.mirror.web3.state;

import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.mirror.web3.state.core.ListReadableQueueState;
import com.hedera.mirror.web3.state.core.ListWritableQueueState;
import com.hedera.mirror.web3.state.core.MapReadableKVState;
import com.hedera.mirror.web3.state.core.MapReadableStates;
import com.hedera.mirror.web3.state.core.MapWritableKVState;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    public MirrorNodeState addService(@NonNull final String serviceName, @NonNull final Map<String, ?> dataSources) {
        final var serviceStates = this.states.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
        dataSources.forEach((k, b) -> {
            if (!serviceStates.containsKey(k)) {
                serviceStates.put(k, b);
            }
        });

        // Purge any readable states whose state definitions are now stale,
        // since they don't include the new data sources we just added
        readableStates.remove(serviceName);
        writableStates.remove(serviceName);
        return this;
    }

    /**
     * Removes the state with the given key for the service with the given name.
     *
     * @param serviceName the name of the service
     * @param stateKey the key of the state
     */
    public void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {
        requireNonNull(serviceName);
        requireNonNull(stateKey);
        this.states.computeIfPresent(serviceName, (k, v) -> {
            v.remove(stateKey);
            // Purge any readable states whose state definitions are now stale,
            // since they still include the data sources we just removed
            readableStates.remove(serviceName);
            writableStates.remove(serviceName);
            return v;
        });
    }

    @Nonnull
    @Override
    public ReadableStates getReadableStates(@Nonnull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = this.states.get(s);
            if (serviceStates == null) {
                return new MapReadableStates(new HashMap<>());
            }
            final Map<String, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue queue) {
                    data.put(stateName, new ListReadableQueueState(stateName, queue));
                } else if (state instanceof Map map) {
                    data.put(stateName, new MapReadableKVState(stateName, map));
                } else if (state instanceof AtomicReference ref) {
                    data.put(stateName, new ReadableSingletonStateBase<>(stateName, ref::get));
                }
            }
            return new MapReadableStates(data);
        });
    }

    @Nonnull
    @Override
    public WritableStates getWritableStates(@Nonnull String serviceName) {
        return writableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = states.get(s);
            if (serviceStates == null) {
                return new EmptyWritableStates();
            }
            final Map<String, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue<?> queue) {
                    data.put(
                            stateName,
                            withAnyRegisteredListeners(serviceName, new ListWritableQueueState<>(stateName, queue)));
                } else if (state instanceof Map<?, ?> map) {
                    data.put(
                            stateName,
                            withAnyRegisteredListeners(serviceName, new MapWritableKVState<>(stateName, map)));
                } else if (state instanceof AtomicReference<?> ref) {
                    data.put(stateName, withAnyRegisteredListeners(serviceName, stateName, ref));
                }
            }
            return new MapWritableStates(data, () -> readableStates.remove(serviceName));
        });
    }

    @Override
    public void registerCommitListener(@NonNull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void unregisterCommitListener(@NonNull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.remove(listener);
    }

    public void commit() {
        writableStates.values().forEach(writableStatesValue -> {
            if (writableStatesValue instanceof MapWritableStates mapWritableStates) {
                mapWritableStates.commit();
            }
        });
    }

    private <V> WritableSingletonStateBase<V> withAnyRegisteredListeners(
            @NonNull final String serviceName, @NonNull final String stateKey, @NonNull final AtomicReference<V> ref) {
        final var state = new WritableSingletonStateBase<>(stateKey, ref::get, ref::set);
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(SINGLETON)) {
                registerSingletonListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <K, V> MapWritableKVState<K, V> withAnyRegisteredListeners(
            @NonNull final String serviceName, @NonNull final MapWritableKVState<K, V> state) {
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(MAP)) {
                registerKVListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <T> ListWritableQueueState<T> withAnyRegisteredListeners(
            @NonNull final String serviceName, @NonNull final ListWritableQueueState<T> state) {
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(QUEUE)) {
                registerQueueListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <V> void registerSingletonListener(
            @NonNull final String serviceName,
            @NonNull final WritableSingletonStateBase<V> singletonState,
            @NonNull final StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, singletonState.getStateKey());
        singletonState.registerListener(value -> listener.singletonUpdateChange(stateId, value));
    }

    private <V> void registerQueueListener(
            @NonNull final String serviceName,
            @NonNull final WritableQueueStateBase<V> queueState,
            @NonNull final StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, queueState.getStateKey());
        queueState.registerListener(new QueueChangeListener<>() {
            @Override
            public void queuePushChange(@NonNull final V value) {
                listener.queuePushChange(stateId, value);
            }

            @Override
            public void queuePopChange() {
                listener.queuePopChange(stateId);
            }
        });
    }

    private <K, V> void registerKVListener(
            @NonNull final String serviceName, WritableKVStateBase<K, V> state, StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, state.getStateKey());
        state.registerListener(new KVChangeListener<>() {
            @Override
            public void mapUpdateChange(@NonNull final K key, @NonNull final V value) {
                listener.mapUpdateChange(stateId, key, value);
            }

            @Override
            public void mapDeleteChange(@NonNull final K key) {
                listener.mapDeleteChange(stateId, key);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MirrorNodeState that = (MirrorNodeState) o;
        return Objects.equals(readableStates, that.readableStates)
                && Objects.equals(writableStates, that.writableStates)
                && Objects.equals(states, that.states)
                && Objects.equals(listeners, that.listeners);
    }

    @Override
    public int hashCode() {
        return Objects.hash(readableStates, writableStates, states, listeners);
    }

    @VisibleForTesting
    void setWritableStates(final Map<String, WritableStates> writableStates) {
        this.writableStates.putAll(writableStates);
    }
}
