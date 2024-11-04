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

import com.hedera.mirror.web3.state.utils.MapReadableStates;
import com.hedera.mirror.web3.state.utils.MapWritableKVState;
import com.hedera.mirror.web3.state.utils.MapWritableStates;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Named
public class MirrorNodeState implements State {
    private final Map<String, ReadableKVState<?, ?>> tokenReadableServiceStates = new HashMap<>();
    private final Map<String, ReadableKVState<?, ?>> contractReadableServiceStates = new HashMap<>();
    private final Map<String, ReadableKVState<?, ?>> fileReadableServiceStates = new HashMap<>();

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();

    public MirrorNodeState(
            final AccountReadableKVState accountReadableKVState,
            final AirdropsReadableKVState airdropsReadableKVState,
            final AliasesReadableKVState aliasesReadableKVState,
            final ContractBytecodeReadableKVState contractBytecodeReadableKVState,
            final ContractStorageReadableKVState contractStorageReadableKVState,
            final FileReadableKVState fileReadableKVState,
            final NftReadableKVState nftReadableKVState,
            final TokenReadableKVState tokenReadableKVState,
            final TokenRelationshipReadableKVState tokenRelationshipReadableKVState) {

        tokenReadableServiceStates.put("ACCOUNTS", accountReadableKVState);
        tokenReadableServiceStates.put("PENDING_AIRDROPS", airdropsReadableKVState);
        tokenReadableServiceStates.put("ALIASES", aliasesReadableKVState);
        tokenReadableServiceStates.put("NFTS", nftReadableKVState);
        tokenReadableServiceStates.put("TOKENS", tokenReadableKVState);
        tokenReadableServiceStates.put("TOKEN_RELS", tokenRelationshipReadableKVState);

        contractReadableServiceStates.put("BYTECODE", contractBytecodeReadableKVState);
        contractReadableServiceStates.put("STORAGE", contractStorageReadableKVState);

        fileReadableServiceStates.put("FILES", fileReadableKVState);
    }

    @Nonnull
    @Override
    public ReadableStates getReadableStates(@Nonnull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            switch (s) {
                case TokenService.NAME -> {
                    return new MapReadableStates(tokenReadableServiceStates);
                }
                case ContractService.NAME -> {
                    return new MapReadableStates(contractReadableServiceStates);
                }
                case FileService.NAME -> {
                    return new MapReadableStates(fileReadableServiceStates);
                }
                default -> {
                    return new MapReadableStates(Collections.emptyMap());
                }
            }
        });
    }

    @Nonnull
    @Override
    public WritableStates getWritableStates(@Nonnull String serviceName) {
        return switch (serviceName) {
            case TokenService.NAME -> new MapWritableStates(getWritableStates(tokenReadableServiceStates));
            case ContractService.NAME -> new MapWritableStates(getWritableStates(contractReadableServiceStates));
            case FileService.NAME -> new MapWritableStates(getWritableStates(fileReadableServiceStates));
            default -> new MapWritableStates(Collections.emptyMap());
        };
    }

    private Map<String, ?> getWritableStates(final Map<String, ReadableKVState<?, ?>> readableStates) {
        final Map<String, Object> data = new HashMap<>();
        readableStates.forEach(((s, readableKVState) ->
                data.put(s, new MapWritableKVState<>(readableKVState.getStateKey(), readableKVState))));
        return data;
    }
}
