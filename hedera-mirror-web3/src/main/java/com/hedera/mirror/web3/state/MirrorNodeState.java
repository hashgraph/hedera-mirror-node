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

import static com.hedera.node.app.util.FileUtilities.createFileID;
import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.web3.state.components.MetricsImpl;
import com.hedera.mirror.web3.state.utils.ListReadableQueueState;
import com.hedera.mirror.web3.state.utils.ListWritableQueueState;
import com.hedera.mirror.web3.state.utils.MapReadableKVState;
import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
@RequiredArgsConstructor
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    private final AccountReadableKVState accountReadableKVState;
    private final AirdropsReadableKVState airdropsReadableKVState;
    private final AliasesReadableKVState aliasesReadableKVState;
    private final ContractBytecodeReadableKVState contractBytecodeReadableKVState;
    private final ContractStorageReadableKVState contractStorageReadableKVState;
    private final FileReadableKVState fileReadableKVState;
    private final NftReadableKVState nftReadableKVState;
    private final TokenReadableKVState tokenReadableKVState;
    private final TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    private final ServicesRegistry servicesRegistry;
    private final ServiceMigrator serviceMigrator;
    private final NetworkInfo networkInfo;

    @PostConstruct
    private void init() {
        registerServices(servicesRegistry);
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        serviceMigrator.doMigrations(
                this,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                new MetricsImpl());

        final var writableStates = this.getWritableStates(FileService.NAME);
        final var files = writableStates.<FileID, File>get(V0490FileSchema.BLOBS_KEY);
        genesisContentProviders(networkInfo, bootstrapConfig).forEach((fileNum, provider) -> {
            final var fileId = createFileID(fileNum, bootstrapConfig);
            files.put(
                    fileId,
                    File.newBuilder()
                            .fileId(fileId)
                            .keys(KeyList.DEFAULT)
                            .contents(provider.apply(bootstrapConfig))
                            .build());
        });
        ((CommittableWritableStates) writableStates).commit();
        try {
            KZGPointEvalPrecompiledContract.init();
        } catch (NoSuchMethodError e) {
            // ignore
        }
        accountReadableKVState.reset();
    }

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
                    switch (stateName) {
                        case "ACCOUNTS" -> data.put(stateName, accountReadableKVState);
                        case "PENDING_AIRDROPS" -> data.put(stateName, airdropsReadableKVState);
                        case "ALIASES" -> data.put(stateName, aliasesReadableKVState);
                        case "FILES" -> data.put(stateName, fileReadableKVState);
                        case "BYTECODE" -> data.put(stateName, contractBytecodeReadableKVState);
                        case "STORAGE" -> data.put(stateName, contractStorageReadableKVState);
                        case "NFTS" -> data.put(stateName, nftReadableKVState);
                        case "TOKENS" -> data.put(stateName, tokenReadableKVState);
                        case "TOKEN_RELS" -> data.put(stateName, tokenRelationshipReadableKVState);
                        default -> data.put(stateName, new MapReadableKVState(stateName, map));
                    }
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
                            withAnyRegisteredListeners(
                                    serviceName,
                                    new MapWritableKVState<>(
                                            stateName,
                                            getReadableStates(serviceName).get(stateName))));
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

    private void registerServices(ServicesRegistry servicesRegistry) {
        // Register all service schema RuntimeConstructable factories before platform init
        final var appContext = new AppContextImpl(InstantSource.system(), signatureVerifier());
        Set.of(
                        new EntityIdService(),
                        new TokenServiceImpl(),
                        new FileServiceImpl(),
                        new ContractServiceImpl(appContext),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new RecordCacheService())
                .forEach(servicesRegistry::register);
    }

    private Map<Long, Function<Configuration, Bytes>> genesisContentProviders(
            final NetworkInfo networkInfo, final com.swirlds.config.api.Configuration config) {
        final var genesisSchema = new V0490FileSchema();
        final var filesConfig = config.getConfigData(FilesConfig.class);
        return Map.of(
                filesConfig.addressBook(), ignore -> genesisSchema.genesisAddressBook(networkInfo),
                filesConfig.nodeDetails(), ignore -> genesisSchema.genesisNodeDetails(networkInfo),
                filesConfig.feeSchedules(), genesisSchema::genesisFeeSchedules,
                filesConfig.exchangeRates(), genesisSchema::genesisExchangeRates,
                filesConfig.networkProperties(), genesisSchema::genesisNetworkProperties,
                filesConfig.hapiPermissions(), genesisSchema::genesisHapiPermissions,
                filesConfig.throttleDefinitions(), genesisSchema::genesisThrottleDefinitions);
    }

    private SignatureVerifier signatureVerifier() {
        return new SignatureVerifier() {
            @Override
            public boolean verifySignature(
                    @Nonnull Key key,
                    @Nonnull com.hedera.pbj.runtime.io.buffer.Bytes bytes,
                    @Nonnull com.hedera.node.app.spi.signatures.SignatureVerifier.MessageType messageType,
                    @Nonnull SignatureMap signatureMap,
                    @Nullable Function<Key, SimpleKeyStatus> simpleKeyVerifier) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public KeyCounts countSimpleKeys(@Nonnull Key key) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }
}
