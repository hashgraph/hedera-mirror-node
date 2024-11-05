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

import static java.util.Objects.requireNonNull;

import com.hedera.mirror.web3.state.utils.MapReadableKVState;
import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyReadableStates;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();

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
            readableStates.remove(serviceName); // Remove the service so that its states will be repopulated.
            return v;
        });
    }

    @Nonnull
    @Override
    public ReadableStates getReadableStates(@Nonnull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = this.states.get(s);
            if (serviceStates == null) {
                return new EmptyReadableStates();
            }
            final Map<String, Object> states = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Map map) {
                    states.put(stateName, new MapReadableKVState(stateName, map));
                }
            }
            return new MapReadableStates(states);
        });
    }

    @Nonnull
    @Override
    public WritableStates getWritableStates(@Nonnull String serviceName) {
        final var serviceStates = states.get(serviceName);
        if (serviceStates == null) {
            return new EmptyWritableStates();
        }

        final Map<String, Object> data = new ConcurrentHashMap<>();
        for (final var entry : serviceStates.entrySet()) {
            final var stateName = entry.getKey();
            final var state = entry.getValue();
            if (state instanceof Map) {
                final var readableState = getReadableStates(serviceName).get(stateName);
                data.put(stateName, new MapWritableKVState<>(stateName, readableState));
            }
        }
        return new MapWritableStates(data);
    }
}
