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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges.Builder;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Named
public class ServiceMigratorImpl implements ServiceMigrator {

    @Override
    public List<Builder> doMigrations(
            @Nonnull State state,
            @Nonnull ServicesRegistry servicesRegistry,
            @Nullable SoftwareVersion previousVersion,
            @Nonnull SoftwareVersion currentVersion,
            @Nonnull Configuration nodeConfiguration,
            @Nonnull Configuration platformConfiguration,
            @Nullable NetworkInfo genesisNetworkInfo,
            @Nonnull Metrics metrics,
            @Nonnull StartupNetworks startupNetworks) {
        requireNonNull(state);
        requireNonNull(servicesRegistry);
        requireNonNull(currentVersion);
        requireNonNull(nodeConfiguration);
        requireNonNull(platformConfiguration);
        requireNonNull(genesisNetworkInfo);
        requireNonNull(metrics);

        if (!(state instanceof MirrorNodeState mirrorNodeState)) {
            throw new IllegalArgumentException("Can only be used with MirrorNodeState instances");
        }

        if (!(servicesRegistry instanceof ServicesRegistryImpl registry)) {
            throw new IllegalArgumentException("Can only be used with ServicesRegistryImpl instances");
        }

        final AtomicLong prevEntityNum = new AtomicLong(
                nodeConfiguration.getConfigData(HederaConfig.class).firstUserEntity() - 1);
        final Map<String, Object> sharedValues = new HashMap<>();
        final var deserializedPbjVersion = Optional.ofNullable(previousVersion)
                .map(SoftwareVersion::getPbjSemanticVersion)
                .orElse(null);

        registry.registrations().stream().forEach(registration -> {
            if (!(registration.registry() instanceof SchemaRegistryImpl schemaRegistry)) {
                throw new IllegalArgumentException("Can only be used with SchemaRegistryImpl instances");
            }
            schemaRegistry.migrate(
                    registration.serviceName(),
                    mirrorNodeState,
                    deserializedPbjVersion,
                    genesisNetworkInfo,
                    platformConfiguration,
                    sharedValues,
                    prevEntityNum,
                    startupNetworks);
        });
        return List.of();
    }

    @Nullable
    @Override
    public SemanticVersion creationVersionOf(@Nonnull State state) {
        if (!(state instanceof MirrorNodeState)) {
            throw new IllegalArgumentException("Can only be used with MirrorNodeState instances");
        }
        return null;
    }
}
