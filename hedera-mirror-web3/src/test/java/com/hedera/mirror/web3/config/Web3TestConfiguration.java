/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.config;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.resources.ContractDeployer;
import com.hedera.mirror.web3.utils.TestWeb3jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@TestConfiguration(proxyBeanMethods = false)
public class Web3TestConfiguration {
    private static final String MOCK_KEY = "0x4e3c5c727f3f4b8f8e8a8fe7e032cf78b8693a2b711e682da1d3a26a6a3b58b6";

    @Autowired
    ContractCallService contractCallService;

    @Bean
    ContractDeployer contractDeployer(DomainBuilder domainBuilder) {
        final var mockEcKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(MOCK_KEY));
        final var credentials = Credentials.create(mockEcKeyPair);
        final var contractGasProvider = new DefaultGasProvider();
        final var web3j = Web3j.build(new TestWeb3jService());

        return new ContractDeployer(domainBuilder, web3j, credentials, contractGasProvider, contractCallService);
    }
}
