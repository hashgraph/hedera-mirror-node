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

package com.hedera.mirror.grpc;

import com.hedera.mirror.common.domain.DomainBuilder;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionOperations;

// Same database is used for all tests, so clean it up before each test.
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:cleanup.sql")
@SpringBootTest
@Import(GrpcIntegrationTest.Config.class)
public abstract class GrpcIntegrationTest {

    protected final Logger log = LogManager.getLogger(getClass());

    @Resource
    private Collection<CacheManager> cacheManagers;

    @Resource
    protected DomainBuilder domainBuilder;

    @BeforeEach
    void logTest(TestInfo testInfo) {
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    private void reset() {
        cacheManagers.forEach(
                c -> c.getCacheNames().forEach(name -> c.getCache(name).clear()));
    }

    @TestConfiguration
    static class Config {
        @Bean
        DomainBuilder domainBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
            return new DomainBuilder(entityManager, transactionOperations);
        }
    }
}
