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

package com.hedera.mirror.web3;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.web3.config.IntegrationTestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:cleanup.sql")
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
public abstract class Web3IntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    protected DomainBuilder domainBuilder;

    @Resource
    protected MeterRegistry meterRegistry;

    @Resource
    private Collection<CacheManager> cacheManagers;

    protected void reset() {
        cacheManagers.forEach(
                c -> c.getCacheNames().forEach(name -> c.getCache(name).clear()));
    }

    @BeforeEach
    void logTest(TestInfo testInfo) {
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }
}
