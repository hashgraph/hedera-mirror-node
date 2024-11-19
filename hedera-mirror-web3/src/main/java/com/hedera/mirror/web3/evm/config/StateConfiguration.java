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

package com.hedera.mirror.web3.evm.config;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.AccountReadableKVState;
import com.hedera.mirror.web3.state.AirdropsReadableKVState;
import com.hedera.mirror.web3.state.AliasesReadableKVState;
import com.hedera.mirror.web3.state.ContractBytecodeReadableKVState;
import com.hedera.mirror.web3.state.ContractStorageReadableKVState;
import com.hedera.mirror.web3.state.FileReadableKVState;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.state.NftReadableKVState;
import com.hedera.mirror.web3.state.TokenReadableKVState;
import com.hedera.mirror.web3.state.TokenRelationshipReadableKVState;
import com.hedera.mirror.web3.state.components.NetworkInfoImpl;
import com.hedera.mirror.web3.state.components.ServiceMigratorImpl;
import com.hedera.mirror.web3.state.components.ServicesRegistryImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.spi.info.NetworkInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for beans related to state
 */
@Configuration
public class StateConfiguration {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public StateConfiguration(MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    @Bean
    MirrorNodeState mirrorNodeState(
            AccountReadableKVState accountReadableKVState,
            AirdropsReadableKVState airdropsReadableKVState,
            AliasesReadableKVState aliasesReadableKVState,
            ContractBytecodeReadableKVState contractBytecodeReadableKVState,
            ContractStorageReadableKVState contractStorageReadableKVState,
            FileReadableKVState fileReadableKVState,
            NftReadableKVState nftReadableKVState,
            TokenReadableKVState tokenReadableKVState,
            TokenRelationshipReadableKVState tokenRelationshipReadableKVState,
            ServicesRegistry servicesRegistry,
            ServiceMigrator serviceMigrator,
            NetworkInfo networkInfo) {
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

    @Bean
    ServicesRegistry servicesRegistry() {
        return new ServicesRegistryImpl();
    }

    @Bean
    ServiceMigrator serviceMigrator() {
        return new ServiceMigratorImpl();
    }

    @Bean
    NetworkInfo networkInfo() {
        return new NetworkInfoImpl(mirrorNodeEvmProperties);
    }
}
