package com.hedera.mirror.importer.parser.performance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.sql.SQLException;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.hedera.mirror.importer.db.DBProperties;

@Log4j2
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SeededDbIntegrationTest extends PerformanceIntegrationTest {
    @Resource
    private DBProperties dbProperties;

    private DBProperties dbPropertiesCache;

    @Rule
    GenericContainer customContainer;

    @Resource
    PostgreSQLContainer postgresql;

    @Autowired
    private ConfigurableApplicationContext context;

    @BeforeAll
    void warmUp() throws SQLException {
        dbPropertiesCache = dbProperties.toBuilder().build();

        log.info("Stored dbProperties were {}", dbPropertiesCache);
        customContainer = CustomPostgresContainer.createSeededContainer(
                "data/seeded-image/Dockerfile",
                "testnet_100k_pgdump.gz",
                6432,
                dbProperties);

        log.info("Stop injected container {}", postgresql);
        postgresql.stop();

        log.info("Start container {}", customContainer);
        customContainer.start();

        dbProperties.setPort(customContainer.getMappedPort(6432));
        TestPropertyValues
                .of("hedera.mirror.importer.db.port=" + customContainer.getMappedPort(5432))
                .applyTo(context);
        log.info("dbProperties were set to {}", dbProperties);
        connection = dataSource.getConnection();
        checkSeededTablesArePresent();

        parse("2020-02-09T18_30_00.000084Z.rcd");
    }

    @AfterAll
    void coolOff() {
        log.info("Reset dbProperties to {}", dbPropertiesCache);
//        setDbProperties(
//                dbPropertiesCache.getHost(),
//                dbPropertiesCache.getName(),
//                dbPropertiesCache.getUsername(),
//                dbPropertiesCache.getPassword(),
//                dbPropertiesCache.getPort());

        log.info("Stop container {}", customContainer);
        customContainer.stop();

        log.info("Start injected container {}", postgresql);
        postgresql.start();
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(15)
    @Test
    public void parseAndIngestTransactions() throws Exception {
        clearLastProcessedRecordHash();
        parse("*.rcd");
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(1)
    @Test
    public void checkEntitiesTablesIsPopulated() throws Exception {
        verifyTableSize("t_entities", "entities");
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(2)
    @Test
    public void checkBalancesTablesIsPopulated() throws Exception {
        verifyTableSize("account_balances", "balances");
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(1)
    @Test
    public void checkTopicMessageTablesIsPopulated() throws Exception {
        verifyTableSize("topic_message", "topicmessages");
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(1)
    @Test
    public void checkTransactionsTablesIsPopulated() throws Exception {
        verifyTableSize("t_transactions", "transactions");
    }

    private void setDbProperties(String host, String name, String username, String password, int port) {
        TestPropertyValues
                .of("hedera.mirror.importer.db.port=" + customContainer.getMappedPort(port))
                .and("hedera.mirror.importer.db.host=" + host)
                .and("hedera.mirror.importer.db.name=" + name)
                .and("hedera.mirror.importer.db.username=" + username)
                .and("hedera.mirror.importer.db.password=" + password)
                .applyTo(context);
    }
}
