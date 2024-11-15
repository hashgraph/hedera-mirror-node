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

package com.hedera.mirror.web3.state.components;

import com.hedera.mirror.web3.state.AccountReadableKVState;
import com.hedera.mirror.web3.state.AirdropsReadableKVState;
import com.hedera.mirror.web3.state.AliasesReadableKVState;
import com.hedera.mirror.web3.state.ContractBytecodeReadableKVState;
import com.hedera.mirror.web3.state.ContractStorageReadableKVState;
import com.hedera.mirror.web3.state.FileReadableKVState;
import com.hedera.mirror.web3.state.NftReadableKVState;
import com.hedera.mirror.web3.state.TokenReadableKVState;
import com.hedera.mirror.web3.state.TokenRelationshipReadableKVState;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.state.spi.Service;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class ServicesRegistryImpl implements ServicesRegistry {

    private final SortedSet<Registration> entries = new TreeSet<>();

    private final AccountReadableKVState accountReadableKVState;
    private final AirdropsReadableKVState airdropsReadableKVState;
    private final AliasesReadableKVState aliasesReadableKVState;
    private final ContractBytecodeReadableKVState contractBytecodeReadableKVState;
    private final ContractStorageReadableKVState contractStorageReadableKVState;
    private final FileReadableKVState fileReadableKVState;
    private final NftReadableKVState nftReadableKVState;
    private final TokenReadableKVState tokenReadableKVState;
    private final TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    @Nonnull
    @Override
    public Set<Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }

    @Override
    public void register(@Nonnull Service service) {
        final var registry = new SchemaRegistryImpl(
                new SchemaApplications(),
                accountReadableKVState,
                airdropsReadableKVState,
                aliasesReadableKVState,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                fileReadableKVState,
                nftReadableKVState,
                tokenReadableKVState,
                tokenRelationshipReadableKVState);
        service.registerSchemas(registry);
        entries.add(new ServicesRegistryImpl.Registration(service, registry));
    }

    @Nonnull
    @Override
    public ServicesRegistry subRegistryFor(@Nonnull String... serviceNames) {
        final var selections = Set.of(serviceNames);
        final var subRegistry = new ServicesRegistryImpl(
                accountReadableKVState,
                airdropsReadableKVState,
                aliasesReadableKVState,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                fileReadableKVState,
                nftReadableKVState,
                tokenReadableKVState,
                tokenRelationshipReadableKVState);
        subRegistry.entries.addAll(entries.stream()
                .filter(registration -> selections.contains(registration.serviceName()))
                .toList());
        return subRegistry;
    }
}
