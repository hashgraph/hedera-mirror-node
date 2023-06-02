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

package com.hedera.mirror.graphql;

import com.hedera.mirror.common.domain.DomainBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionOperations;

@Import(GraphqlIntegrationTest.IntegrationTestConfiguration.class)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/cleanup.sql")
@SpringBootTest
public abstract class GraphqlIntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    protected DomainBuilder domainBuilder;

    @BeforeEach
    void logTest(TestInfo testInfo) {
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    @TestConfiguration
    static class IntegrationTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        DomainBuilder domainBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
            return new DomainBuilder(entityManager, transactionOperations);
        }
    }
}
