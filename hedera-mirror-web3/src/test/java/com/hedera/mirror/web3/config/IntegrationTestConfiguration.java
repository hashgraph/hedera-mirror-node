/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionOperations;

@TestConfiguration
public class IntegrationTestConfiguration {

    @Bean
    DomainBuilder domainBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        return new DomainBuilder(entityManager, transactionOperations);
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
