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

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.GenericContainer;

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

    @BeforeAll
    void warmUp() throws SQLException {
        dbPropertiesCache = dbProperties.toBuilder().build();
        customContainer = CustomPostgresContainer.createSeededContainer(
                "data/seeded-image/Dockerfile",
                "testnet_100k_pgdump.gz",
                5432);

        log.info("Start container {}", customContainer);
        customContainer.start();
        setDbProperties(
                "127.0.0.1",
                "mirror_node",
                "mirror_node_pass",
                5432);

        connection = dataSource.getConnection();
        checkSeededTablesArePresent();

        log.info("dbProperties were set to {}", dbProperties);
        parse("2020-02-09T18_30_00.000084Z.rcd");
    }

    @AfterAll
    void coolOff() {
        log.info("Reset dbProperties to {}", dbPropertiesCache);
        setDbProperties(
                dbPropertiesCache.getHost(),
                dbPropertiesCache.getName(),
                dbPropertiesCache.getPassword(),
                dbPropertiesCache.getPort());

        log.info("Stop container {}", customContainer);
        customContainer.stop();
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Timeout(15)
    @Test
    public void parseAndIngestTransactions() throws Exception {
        long entitiesStartCount = getTableSize("t_entities");
        long balanceStartCount = getTableSize("account_balances");
        long topicMessagesStartCount = getTableSize("topic_message");
        long transactionsStartCount = getTableSize("t_transactions");
        log.info("{} entities, {} balances, {} topic messages and {} transactions were seeded", entitiesStartCount,
                balanceStartCount, topicMessagesStartCount, transactionsStartCount);

        clearLastProcessedRecordHash();
        parse("*.rcd");

        // verify table sizes grew and transactions were ingested to expected table
        long entitiesEndCount = getTableSize("t_entities");
        long balanceEndCount = getTableSize("account_balances");
        long topicMessagesEndCount = getTableSize("topic_message");
        long transactionsEndCount = getTableSize("t_transactions");
        log.info("{} entities, {} balances, {} topic messages and {} transactions were seeded", entitiesEndCount,
                balanceEndCount, topicMessagesEndCount, transactionsEndCount);
        assertThat(entitiesEndCount).isGreaterThan(entitiesStartCount);
        assertThat(balanceEndCount).isGreaterThan(balanceStartCount);
        assertThat(topicMessagesEndCount).isGreaterThan(topicMessagesStartCount);
        assertThat(transactionsEndCount).isGreaterThan(transactionsStartCount);
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

    private void setDbProperties(String host, String name, String password, int port) {
        dbProperties.setHost(host);
        dbProperties.setName(name);
        dbProperties.setPassword(password);
        dbProperties.setPort(port);
    }
}
