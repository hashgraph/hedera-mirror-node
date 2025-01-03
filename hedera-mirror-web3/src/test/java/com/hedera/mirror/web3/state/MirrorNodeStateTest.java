/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.core.MapReadableStates;
import com.hedera.mirror.web3.state.core.MapWritableKVState;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.StateChangeListener.StateType;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableStates;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class MirrorNodeStateTest {

    @InjectMocks
    private MirrorNodeState mirrorNodeState;

    @Mock
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private AirdropsReadableKVState airdropsReadableKVState;

    @Mock
    private AliasesReadableKVState aliasesReadableKVState;

    @Mock
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private FileReadableKVState fileReadableKVState;

    @Mock
    private NftReadableKVState nftReadableKVState;

    @Mock
    private TokenReadableKVState tokenReadableKVState;

    @Mock
    private TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    @Mock
    private ServicesRegistry servicesRegistry;

    @Mock
    private ServiceMigrator serviceMigrator;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private StateChangeListener listener;

    private List<ReadableKVState> readableKVStates;

    @BeforeEach
    void setup() {
        readableKVStates = new LinkedList<>();
        readableKVStates.add(accountReadableKVState);
        readableKVStates.add(airdropsReadableKVState);
        readableKVStates.add(aliasesReadableKVState);
        readableKVStates.add(contractBytecodeReadableKVState);
        readableKVStates.add(contractStorageReadableKVState);
        readableKVStates.add(fileReadableKVState);
        readableKVStates.add(nftReadableKVState);
        readableKVStates.add(tokenReadableKVState);
        readableKVStates.add(tokenRelationshipReadableKVState);

        mirrorNodeState = initStateAfterMigration();
    }

    @Test
    void testAddService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        assertThat(mirrorNodeState.getReadableStates("NEW").contains(FileReadableKVState.KEY))
                .isFalse();
        final var newState = mirrorNodeState.addService(
                "NEW",
                new HashMap<>(Map.of(FileReadableKVState.KEY, Map.of(FileReadableKVState.KEY, fileReadableKVState))));
        assertThat(newState.getReadableStates("NEW").contains(FileReadableKVState.KEY))
                .isTrue();
    }

    @Test
    void testRemoveService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var testStates = new HashMap<>(Map.of(
                ContractBytecodeReadableKVState.KEY,
                        Map.of(ContractBytecodeReadableKVState.KEY, contractBytecodeReadableKVState),
                ContractStorageReadableKVState.KEY,
                        Map.of(ContractStorageReadableKVState.KEY, contractStorageReadableKVState)));
        final var newState = mirrorNodeState.addService("NEW", testStates);
        assertThat(newState.getReadableStates("NEW").contains(ContractBytecodeReadableKVState.KEY))
                .isTrue();
        assertThat(newState.getReadableStates("NEW").contains(ContractStorageReadableKVState.KEY))
                .isTrue();
        newState.removeServiceState("NEW", ContractBytecodeReadableKVState.KEY);
        assertThat(newState.getReadableStates("NEW").contains(ContractBytecodeReadableKVState.KEY))
                .isFalse();
        assertThat(newState.getReadableStates("NEW").contains(ContractStorageReadableKVState.KEY))
                .isTrue();
    }

    @Test
    void testGetReadableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(FileReadableKVState.KEY, fileReadableKVState)));
    }

    @Test
    void testGetReadableStatesForContractService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        ContractBytecodeReadableKVState.KEY,
                        contractBytecodeReadableKVState,
                        ContractStorageReadableKVState.KEY,
                        contractStorageReadableKVState)));
    }

    @Test
    void testGetReadableStatesForTokenService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(nftReadableKVState.getStateKey()).thenReturn(NftReadableKVState.KEY);
        when(tokenReadableKVState.getStateKey()).thenReturn(TokenReadableKVState.KEY);
        when(tokenRelationshipReadableKVState.getStateKey()).thenReturn(TokenRelationshipReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        AccountReadableKVState.KEY,
                        accountReadableKVState,
                        AirdropsReadableKVState.KEY,
                        airdropsReadableKVState,
                        AliasesReadableKVState.KEY,
                        aliasesReadableKVState,
                        NftReadableKVState.KEY,
                        nftReadableKVState,
                        TokenReadableKVState.KEY,
                        tokenReadableKVState,
                        TokenRelationshipReadableKVState.KEY,
                        tokenRelationshipReadableKVState)));
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void testGetReadableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        stateWithSingleton.addService(EntityIdService.NAME, Map.of("EntityId", new AtomicReference<>(1L)));
        final var readableStates = stateWithSingleton.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains("EntityId")).isTrue();
        assertThat(readableStates.getSingleton("EntityId").get()).isEqualTo(1L);
    }

    @Test
    void testGetReadableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(
                EntityIdService.NAME, Map.of("EntityId", new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var readableStates = stateWithQueue.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains("EntityId")).isTrue();
        assertThat(readableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.KEY,
                        new MapWritableKVState<>(
                                FileReadableKVState.KEY, readableStates.get(FileReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForFileServiceWithListeners() {
        when(listener.stateTypes()).thenReturn(Set.of(StateType.MAP));
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        mirrorNodeState.registerCommitListener(listener);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.KEY,
                        new MapWritableKVState<>(
                                FileReadableKVState.KEY, readableStates.get(FileReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        ContractBytecodeReadableKVState.KEY,
                        new MapWritableKVState<>(
                                ContractBytecodeReadableKVState.KEY,
                                readableStates.get(ContractBytecodeReadableKVState.KEY)),
                        ContractStorageReadableKVState.KEY,
                        new MapWritableKVState<>(
                                ContractStorageReadableKVState.KEY,
                                readableStates.get(ContractStorageReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForTokenService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(nftReadableKVState.getStateKey()).thenReturn(NftReadableKVState.KEY);
        when(tokenReadableKVState.getStateKey()).thenReturn(TokenReadableKVState.KEY);
        when(tokenRelationshipReadableKVState.getStateKey()).thenReturn(TokenRelationshipReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        AccountReadableKVState.KEY,
                        new MapWritableKVState<>(
                                AccountReadableKVState.KEY, readableStates.get(AccountReadableKVState.KEY)),
                        AirdropsReadableKVState.KEY,
                        new MapWritableKVState<>(
                                AirdropsReadableKVState.KEY, readableStates.get(AirdropsReadableKVState.KEY)),
                        AliasesReadableKVState.KEY,
                        new MapWritableKVState<>(
                                AliasesReadableKVState.KEY, readableStates.get(AliasesReadableKVState.KEY)),
                        NftReadableKVState.KEY,
                        new MapWritableKVState<>(NftReadableKVState.KEY, readableStates.get(NftReadableKVState.KEY)),
                        TokenReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenReadableKVState.KEY, readableStates.get(TokenReadableKVState.KEY)),
                        TokenRelationshipReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenRelationshipReadableKVState.KEY,
                                readableStates.get(TokenRelationshipReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }

    @Test
    void testGetWritableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        stateWithSingleton.addService(EntityIdService.NAME, Map.of("EntityId", new AtomicReference<>(1L)));
        final var writableStates = stateWithSingleton.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getSingleton("EntityId").get()).isEqualTo(1L);
    }

    @Test
    void testGetWritableStatesWithSingletonWithListeners() {
        final var stateWithSingleton = buildStateObject();
        final var ref = new AtomicReference<>(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of("EntityId", ref));
        when(listener.stateTypes()).thenReturn(Set.of(StateType.SINGLETON));
        stateWithSingleton.registerCommitListener(listener);

        final var writableStates = stateWithSingleton.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getSingleton("EntityId").get()).isEqualTo(1L);
    }

    @Test
    void testGetWritableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(
                EntityIdService.NAME, Map.of("EntityId", new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesWithQueueWithListeners() {
        final var stateWithQueue = buildStateObject();
        final var queue = new ConcurrentLinkedDeque<>(Set.of("value"));
        stateWithQueue.addService(EntityIdService.NAME, Map.of("EntityId", queue));
        when(listener.stateTypes()).thenReturn(Set.of(StateType.QUEUE));
        stateWithQueue.registerCommitListener(listener);

        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testRegisterCommitListener() {
        final var state1 = buildStateObject();
        final var state2 = buildStateObject();
        assertThat(state1).isEqualTo(state2);
        state1.registerCommitListener(listener);
        assertThat(state1).isNotEqualTo(state2);
    }

    @Test
    void testUnregisterCommitListener() {
        final var state1 = buildStateObject();
        final var state2 = buildStateObject();
        assertThat(state1).isEqualTo(state2);
        state1.registerCommitListener(listener);
        assertThat(state1).isNotEqualTo(state2);
        state1.unregisterCommitListener(listener);
        assertThat(state1).isEqualTo(state2);
    }

    @Test
    void testCommit() {
        final var state = buildStateObject();
        final var mockMapWritableState = mock(MapWritableStates.class);
        Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();
        writableStates.put(FileService.NAME, mockMapWritableState);
        state.setWritableStates(writableStates);
        state.commit();
        verify(mockMapWritableState, times(1)).commit();
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(mirrorNodeState).isEqualTo(mirrorNodeState);
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mirrorNodeState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mirrorNodeState).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        final var other = initStateAfterMigration();
        assertThat(mirrorNodeState).isEqualTo(other);
    }

    @Test
    void testHashCode() {
        final var other = initStateAfterMigration();
        assertThat(mirrorNodeState).hasSameHashCodeAs(other);
    }

    private MirrorNodeState initStateAfterMigration() {
        final Map<String, Object> fileStateData =
                new HashMap<>(Map.of(FileReadableKVState.KEY, Map.of(FileReadableKVState.KEY, fileReadableKVState)));
        final Map<String, Object> contractStateData = new HashMap<>(Map.of(
                ContractBytecodeReadableKVState.KEY,
                        Map.of(ContractBytecodeReadableKVState.KEY, contractBytecodeReadableKVState),
                ContractStorageReadableKVState.KEY,
                        Map.of(ContractStorageReadableKVState.KEY, contractStorageReadableKVState)));
        final Map<String, Object> tokenStateData = new HashMap<>(Map.of(
                AccountReadableKVState.KEY, Map.of(AccountReadableKVState.KEY, accountReadableKVState),
                AirdropsReadableKVState.KEY, Map.of(AirdropsReadableKVState.KEY, airdropsReadableKVState),
                AliasesReadableKVState.KEY, Map.of(AliasesReadableKVState.KEY, aliasesReadableKVState),
                NftReadableKVState.KEY, Map.of(NftReadableKVState.KEY, nftReadableKVState),
                TokenReadableKVState.KEY, Map.of(TokenReadableKVState.KEY, tokenReadableKVState),
                TokenRelationshipReadableKVState.KEY,
                        Map.of(TokenRelationshipReadableKVState.KEY, tokenRelationshipReadableKVState)));

        // Add service using the mock data source
        return buildStateObject()
                .addService(FileService.NAME, fileStateData)
                .addService(ContractService.NAME, contractStateData)
                .addService(TokenService.NAME, tokenStateData);
    }

    private MirrorNodeState buildStateObject() {
        return new MirrorNodeState(
                readableKVStates,
                servicesRegistry,
                serviceMigrator,
                networkInfo,
                startupNetworks,
                mirrorNodeEvmProperties);
    }
}
