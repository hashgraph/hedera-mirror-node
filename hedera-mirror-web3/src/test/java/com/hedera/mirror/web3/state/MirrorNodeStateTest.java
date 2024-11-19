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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.StateChangeListener.StateType;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import java.util.HashMap;
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
    private NetworkInfo networkInfo;

    @Mock
    private StateChangeListener listener;

    @BeforeEach
    void setup() {
        mirrorNodeState = initStateAfterMigration();
    }

    @Test
    void testAddService() {
        assertThat(mirrorNodeState.getReadableStates("NEW").contains("FILES")).isFalse();
        final var newState =
                mirrorNodeState.addService("NEW", new HashMap<>(Map.of("FILES", Map.of("FILES", fileReadableKVState))));
        assertThat(newState.getReadableStates("NEW").contains("FILES")).isTrue();
    }

    @Test
    void testRemoveService() {
        final var testStates = new HashMap<>(Map.of(
                "BYTECODE", Map.of("BYTECODE", contractBytecodeReadableKVState),
                "STORAGE", Map.of("STORAGE", contractStorageReadableKVState)));
        final var newState = mirrorNodeState.addService("NEW", testStates);
        assertThat(newState.getReadableStates("NEW").contains("BYTECODE")).isTrue();
        assertThat(newState.getReadableStates("NEW").contains("STORAGE")).isTrue();
        newState.removeServiceState("NEW", "BYTECODE");
        assertThat(newState.getReadableStates("NEW").contains("BYTECODE")).isFalse();
        assertThat(newState.getReadableStates("NEW").contains("STORAGE")).isTrue();
    }

    @Test
    void testGetReadableStatesForFileService() {
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(readableStates).isEqualTo(new MapReadableStates(Map.of("FILES", fileReadableKVState)));
    }

    @Test
    void testGetReadableStatesForContractService() {
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        "BYTECODE", contractBytecodeReadableKVState, "STORAGE", contractStorageReadableKVState)));
    }

    @Test
    void testGetReadableStatesForTokenService() {
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        "ACCOUNTS",
                        accountReadableKVState,
                        "PENDING_AIRDROPS",
                        airdropsReadableKVState,
                        "ALIASES",
                        aliasesReadableKVState,
                        "NFTS",
                        nftReadableKVState,
                        "TOKENS",
                        tokenReadableKVState,
                        "TOKEN_RELS",
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
        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(
                        Map.of("FILES", new MapWritableKVState<>("FILES", readableStates.get("FILES")))));
    }

    @Test
    void testGetWritableStatesForFileServiceWithListeners() {
        when(listener.stateTypes()).thenReturn(Set.of(StateType.MAP));
        mirrorNodeState.registerCommitListener(listener);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(
                        Map.of("FILES", new MapWritableKVState<>("FILES", readableStates.get("FILES")))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "BYTECODE",
                        new MapWritableKVState<>("BYTECODE", readableStates.get("BYTECODE")),
                        "STORAGE",
                        new MapWritableKVState<>("STORAGE", readableStates.get("STORAGE")))));
    }

    @Test
    void testGetWritableStatesForTokenService() {
        final var writableStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "ACCOUNTS",
                        new MapWritableKVState<>("ACCOUNTS", readableStates.get("ACCOUNTS")),
                        "PENDING_AIRDROPS",
                        new MapWritableKVState<>("PENDING_AIRDROPS", readableStates.get("PENDING_AIRDROPS")),
                        "ALIASES",
                        new MapWritableKVState<>("ALIASES", readableStates.get("ALIASES")),
                        "NFTS",
                        new MapWritableKVState<>("NFTS", readableStates.get("NFTS")),
                        "TOKENS",
                        new MapWritableKVState<>("TOKENS", readableStates.get("TOKENS")),
                        "TOKEN_RELS",
                        new MapWritableKVState<>("TOKEN_RELS", readableStates.get("TOKEN_RELS")))));
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
        final Map<String, Object> fileStateData = new HashMap<>(Map.of("FILES", Map.of("FILES", fileReadableKVState)));
        final Map<String, Object> contractStateData = new HashMap<>(Map.of(
                "BYTECODE", Map.of("BYTECODE", contractBytecodeReadableKVState),
                "STORAGE", Map.of("STORAGE", contractStorageReadableKVState)));
        final Map<String, Object> tokenStateData = new HashMap<>(Map.of(
                "ACCOUNTS", Map.of("ACCOUNTS", accountReadableKVState),
                "PENDING_AIRDROPS", Map.of("PENDING_AIRDROPS", airdropsReadableKVState),
                "ALIASES", Map.of("ALIASES", aliasesReadableKVState),
                "NFTS", Map.of("NFTS", nftReadableKVState),
                "TOKENS", Map.of("TOKENS", tokenReadableKVState),
                "TOKEN_RELS", Map.of("TOKEN_RELS", tokenRelationshipReadableKVState)));

        // Add service using the mock data source
        return buildStateObject()
                .addService(FileService.NAME, fileStateData)
                .addService(ContractService.NAME, contractStateData)
                .addService(TokenService.NAME, tokenStateData);
    }

    private MirrorNodeState buildStateObject() {
        return new MirrorNodeState(
                accountReadableKVState,
                airdropsReadableKVState,
                aliasesReadableKVState,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                fileReadableKVState,
                nftReadableKVState,
                tokenReadableKVState,
                tokenRelationshipReadableKVState,
                servicesRegistry,
                serviceMigrator,
                networkInfo);
    }
}
