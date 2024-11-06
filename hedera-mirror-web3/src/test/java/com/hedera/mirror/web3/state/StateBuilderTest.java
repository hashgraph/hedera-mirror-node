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

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.state.components.MetricsImpl;
import com.hedera.mirror.web3.state.components.NetworkInfoImpl;
import com.hedera.mirror.web3.state.components.ServiceMigratorImpl;
import com.hedera.mirror.web3.state.components.ServicesRegistryImpl;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.intern.ConfigurationProvider;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StateBuilderTest extends Web3IntegrationTest {

    @Resource
    private NetworkInfoImpl networkInfo;

    private final Configuration DEFAULT_CONFIG =
            ConfigurationProvider.getInstance().createBuilder().build();

    @BeforeEach
    void init() {}

    @Test
    void testBuildState() {
        final var state = new MirrorNodeState();
        final var servicesRegistry = new ServicesRegistryImpl();
        registerServices(servicesRegistry);
        final var migrator = new ServiceMigratorImpl();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                new MetricsImpl());

        final var writableStates = state.getWritableStates(FileService.NAME);
        final var files = writableStates.<FileID, File>get(V0490FileSchema.BLOBS_KEY);
        genesisContentProviders(networkInfo, DEFAULT_CONFIG).forEach((fileNum, provider) -> {
            final var fileId = createFileID(fileNum, DEFAULT_CONFIG);
            files.put(
                    fileId,
                    File.newBuilder()
                            .fileId(fileId)
                            .keys(KeyList.DEFAULT)
                            .contents(provider.apply(DEFAULT_CONFIG))
                            .build());
        });
        ((CommittableWritableStates) writableStates).commit();
    }

    private void registerServices(ServicesRegistry servicesRegistry) {
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(new TokenServiceImpl()).forEach(servicesRegistry::register);
    }

    private Map<Long, Function<Configuration, Bytes>> genesisContentProviders(
            final NetworkInfo networkInfo, final Configuration config) {
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
}
