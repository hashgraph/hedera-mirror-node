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
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.state.utils.MapReadableKVState;
import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @BeforeEach
    void setup() {
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
        mirrorNodeState = mirrorNodeState
                .addService(FileService.NAME, fileStateData)
                .addService(ContractService.NAME, contractStateData)
                .addService(TokenService.NAME, tokenStateData);
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
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(new ConcurrentHashMap<>(
                        Map.of("FILES", new MapReadableKVState("FILES", Map.of("FILES", fileReadableKVState))))));
    }

    @Test
    void testGetReadableStatesForContractService() {
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        "BYTECODE",
                        new MapReadableKVState("BYTECODE", Map.of("BYTECODE", contractBytecodeReadableKVState)),
                        "STORAGE",
                        new MapReadableKVState("STORAGE", Map.of("STORAGE", contractStorageReadableKVState)))));
    }

    @Test
    void testGetReadableStatesForTokenService() {
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        "ACCOUNTS",
                        new MapReadableKVState("ACCOUNTS", Map.of("ACCOUNTS", accountReadableKVState)),
                        "PENDING_AIRDROPS",
                        new MapReadableKVState("PENDING_AIRDROPS", Map.of("PENDING_AIRDROPS", airdropsReadableKVState)),
                        "ALIASES",
                        new MapReadableKVState("ALIASES", Map.of("ALIASES", aliasesReadableKVState)),
                        "NFTS",
                        new MapReadableKVState("NFTS", Map.of("NFTS", nftReadableKVState)),
                        "TOKENS",
                        new MapReadableKVState("TOKENS", Map.of("TOKENS", tokenReadableKVState)),
                        "TOKEN_RELS",
                        new MapReadableKVState("TOKEN_RELS", Map.of("TOKEN_RELS", tokenRelationshipReadableKVState)))));
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void testGetWritableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn("FILES");

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "FILES",
                        new MapWritableKVState<>(fileReadableKVState.getStateKey(), readableStates.get("FILES")))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn("BYTECODE");
        when(contractStorageReadableKVState.getStateKey()).thenReturn("STORAGE");

        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "BYTECODE",
                        new MapWritableKVState<>(
                                contractBytecodeReadableKVState.getStateKey(), readableStates.get("BYTECODE")),
                        "STORAGE",
                        new MapWritableKVState<>(
                                contractStorageReadableKVState.getStateKey(), readableStates.get("STORAGE")))));
    }

    @Test
    void testGetWritableStatesForTokenService() {
        when(accountReadableKVState.getStateKey()).thenReturn("ACCOUNTS");
        when(airdropsReadableKVState.getStateKey()).thenReturn("PENDING_AIRDROPS");
        when(aliasesReadableKVState.getStateKey()).thenReturn("ALIASES");
        when(nftReadableKVState.getStateKey()).thenReturn("NFTS");
        when(tokenReadableKVState.getStateKey()).thenReturn("TOKENS");
        when(tokenRelationshipReadableKVState.getStateKey()).thenReturn("TOKEN_RELS");

        final var writableStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "ACCOUNTS",
                        new MapWritableKVState<>(accountReadableKVState.getStateKey(), readableStates.get("ACCOUNTS")),
                        "PENDING_AIRDROPS",
                        new MapWritableKVState<>(
                                airdropsReadableKVState.getStateKey(), readableStates.get("PENDING_AIRDROPS")),
                        "ALIASES",
                        new MapWritableKVState<>(aliasesReadableKVState.getStateKey(), readableStates.get("ALIASES")),
                        "NFTS",
                        new MapWritableKVState<>(nftReadableKVState.getStateKey(), readableStates.get("NFTS")),
                        "TOKENS",
                        new MapWritableKVState<>(tokenReadableKVState.getStateKey(), readableStates.get("TOKENS")),
                        "TOKEN_RELS",
                        new MapWritableKVState<>(
                                tokenRelationshipReadableKVState.getStateKey(), readableStates.get("TOKEN_RELS")))));
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }
}
