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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.state.AccountReadableKVState;
import com.hedera.mirror.web3.state.AirdropsReadableKVState;
import com.hedera.mirror.web3.state.AliasesReadableKVState;
import com.hedera.mirror.web3.state.ContractBytecodeReadableKVState;
import com.hedera.mirror.web3.state.ContractStorageReadableKVState;
import com.hedera.mirror.web3.state.FileReadableKVState;
import com.hedera.mirror.web3.state.NftReadableKVState;
import com.hedera.mirror.web3.state.TokenReadableKVState;
import com.hedera.mirror.web3.state.TokenRelationshipReadableKVState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.swirlds.state.spi.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ServicesRegistryImplTest {

    private ServicesRegistryImpl servicesRegistry;

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

    ServicesRegistryImplTest() {}

    @BeforeEach
    void setUp() {
        servicesRegistry = new ServicesRegistryImpl(
                accountReadableKVState,
                airdropsReadableKVState,
                aliasesReadableKVState,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                fileReadableKVState,
                nftReadableKVState,
                tokenReadableKVState,
                tokenRelationshipReadableKVState);
    }

    @Test
    void testEmptyRegistrations() {
        assertThat(servicesRegistry.registrations()).isEmpty();
    }

    @Test
    void testRegister() {
        Service service = new FileServiceImpl();
        servicesRegistry.register(service);
        assertThat(servicesRegistry.registrations().size()).isEqualTo(1);
    }

    @Test
    void testEmptySubRegistry() {
        Service service = new FileServiceImpl();
        Service service2 = new EntityIdService();
        ServicesRegistryImpl subRegistry = (ServicesRegistryImpl)
                servicesRegistry.subRegistryFor(service.getServiceName(), service2.getServiceName());
        assertThat(subRegistry.registrations()).isEmpty();
    }

    @Test
    void testSubRegistry() {
        Service service = new FileServiceImpl();
        Service service2 = new EntityIdService();
        servicesRegistry.register(service);
        servicesRegistry.register(service2);
        ServicesRegistryImpl subRegistry = (ServicesRegistryImpl)
                servicesRegistry.subRegistryFor(service.getServiceName(), service2.getServiceName());
        assertThat(subRegistry.registrations().size()).isEqualTo(2);
    }
}
