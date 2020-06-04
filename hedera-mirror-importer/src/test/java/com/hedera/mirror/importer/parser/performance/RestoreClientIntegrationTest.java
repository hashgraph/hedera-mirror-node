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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;

import com.hedera.mirror.importer.db.DBProperties;

@Log4j2
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RestoreClientIntegrationTest extends PerformanceIntegrationTest {
    @Resource
    private DBProperties dbProperties;

    @Rule
    GenericContainer customContainer;

    @BeforeAll
    void warmUp() throws SQLException {
        customContainer = CustomPostgresContainer.createRestoreContainer(
                "data/restore-client/Dockerfile",
                "testnet_100k_pgdump.gz",
                dbProperties);

        log.info("Start container {}", customContainer);
        customContainer.start();
        log.info("Database restore complete to {}", dbProperties);

        connection = dataSource.getConnection();
        checkSeededTablesArePresent();
    }

    @AfterAll
    void coolOff() {
        log.info("Stop container {}", customContainer);
        customContainer.stop();
    }

    @Test
    public void parseAndIngestTransactions() throws Exception {
        clearLastProcessedRecordHash();
        parse("*.rcd");
    }

    @Timeout(1)
    @Test
    public void checkEntitiesTablesIsPopulated() throws Exception {
        verifyTableSize("t_entities", "entities");
    }

    @Timeout(3)
    @Test
    public void checkBalancesTablesIsPopulated() throws Exception {
        verifyTableSize("account_balances", "balances");
    }

    @Timeout(1)
    @Test
    public void checkTopicMessageTablesIsPopulated() throws Exception {
        verifyTableSize("topic_message", "topicmessages");
    }

    @Timeout(1)
    @Test
    public void checkTransactionsTablesIsPopulated() throws Exception {
        verifyTableSize("t_transactions", "transactions");
    }
}
