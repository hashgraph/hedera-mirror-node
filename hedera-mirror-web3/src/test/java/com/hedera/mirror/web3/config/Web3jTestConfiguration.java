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

package com.hedera.mirror.web3.config;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.resources.ContractDeployer;
import com.hedera.mirror.web3.utils.TestWeb3jService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class Web3jTestConfiguration {
    @Bean
    ContractDeployer contractDeployer(
            ContractCallService contractCallService, DomainBuilder domainBuilder, TestWeb3jService testWeb3jService) {
        return new ContractDeployer(domainBuilder, contractCallService, testWeb3jService);
    }

    @Bean
    TestWeb3jService testWeb3jService(ContractCallService contractCallService) {
        return new TestWeb3jService(contractCallService);
    }
}
