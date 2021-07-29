package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.CockroachContainer;

import com.hedera.mirror.importer.config.MeterRegistryConfiguration;

@ActiveProfiles("v2")
@TestExecutionListeners(value = {ResetCacheTestExecutionListener.class},
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
// Same database is used for all tests, so clean it up before each test.
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@SpringBootTest
@Import(MeterRegistryConfiguration.class)
public abstract class IntegrationTest {

    protected static final Logger log = LogManager.getLogger(IntegrationTest.class);

    private static final CockroachContainer COCKROACH = new CockroachContainer("cockroachdb/cockroach:v21.1.6")
            .withLogConsumer(o -> log.info("{}", o.getUtf8String().replace("\n", "")));

    static {
        COCKROACH.start();
    }

    @BeforeEach
    void logTest(TestInfo testInfo) {
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    @SuppressWarnings("unused")
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.password", COCKROACH::getPassword);
        registry.add("spring.datasource.username", COCKROACH::getUsername);
        registry.add("spring.datasource.url", COCKROACH::getJdbcUrl);
        registry.add("spring.flyway.password", COCKROACH::getPassword);
        registry.add("spring.flyway.username", COCKROACH::getUsername);
        registry.add("spring.flyway.url", COCKROACH::getJdbcUrl);
        registry.add("embedded.postgresql.port", () -> COCKROACH.getMappedPort(26257));
        registry.add("embedded.postgresql.host", COCKROACH::getContainerIpAddress);
        registry.add("embedded.postgresql.schema", COCKROACH::getDatabaseName);
        registry.add("embedded.postgresql.user", COCKROACH::getUsername);
        registry.add("embedded.postgresql.password", COCKROACH::getPassword);
    }
}
