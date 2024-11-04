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

import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import java.util.Map;
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
    void testGetWritableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn("FILES");

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "FILES", new MapWritableKVState<>(fileReadableKVState.getStateKey(), fileReadableKVState))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn("BYTECODE");
        when(contractStorageReadableKVState.getStateKey()).thenReturn("STORAGE");

        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "BYTECODE",
                        new MapWritableKVState<>(
                                contractBytecodeReadableKVState.getStateKey(), contractBytecodeReadableKVState),
                        "STORAGE",
                        new MapWritableKVState<>(
                                contractStorageReadableKVState.getStateKey(), contractStorageReadableKVState))));
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
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        "ACCOUNTS",
                        new MapWritableKVState<>(accountReadableKVState.getStateKey(), accountReadableKVState),
                        "PENDING_AIRDROPS",
                        new MapWritableKVState<>(airdropsReadableKVState.getStateKey(), airdropsReadableKVState),
                        "ALIASES",
                        new MapWritableKVState<>(aliasesReadableKVState.getStateKey(), aliasesReadableKVState),
                        "NFTS",
                        new MapWritableKVState<>(nftReadableKVState.getStateKey(), nftReadableKVState),
                        "TOKENS",
                        new MapWritableKVState<>(tokenReadableKVState.getStateKey(), tokenReadableKVState),
                        "TOKEN_RELS",
                        new MapWritableKVState<>(
                                tokenRelationshipReadableKVState.getStateKey(), tokenRelationshipReadableKVState))));
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }
}
