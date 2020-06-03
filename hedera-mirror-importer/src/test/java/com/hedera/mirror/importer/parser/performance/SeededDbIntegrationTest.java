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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Log4j2
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
public class SeededDbIntegrationTest {
    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFileParser recordFileParser;

    @Resource
    private RecordParserProperties parserProperties;

    private FileCopier fileCopier;

    private StreamType streamType;

    @Resource
    private DBProperties dbProperties;

    private DBProperties dbPropertiesCache;

    @Resource
    private DataSource dataSource;

    @Rule
    GenericContainer customContainer;

    @BeforeAll
    void warmUp() {
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

        log.info("dbProperties were set to {}", dbProperties);
        streamType = parserProperties.getStreamType();
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
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
        parse("*.rcd");
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Test
    public void checkSeededTablesArePresent() throws Exception {
        String[] tables = new String[] {"account_balance_sets", "account_balances", "flyway_schema_history",
                "non_fee_transfers", "t_application_status", "t_contract_result", "t_cryptotransferlists",
                "t_entities", "t_entity_types", "t_file_data", "t_livehashes", "t_record_files",
                "t_transaction_results",
                "t_transaction_types", "t_transactions", "topic_message"
        };
        List<String> discoveredTables = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {

            while (rs.next()) {
                discoveredTables.add(rs.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            log.error("Unable to retrieve details from database", e);
        }

        assertThat(discoveredTables.size()).isGreaterThan(0);
        Collections.sort(discoveredTables);
        log.info("Encountered tables: {}", discoveredTables);
        assertThat(discoveredTables).isEqualTo(Arrays.asList(tables));
    }

    @Disabled("Currently still pointing at embedded container instead of customcontianer created here")
    @Test
    public void checkSeededTablesArePopulated() throws Exception {
        long accountsCount = 0;
        long balancesCount = 0;
        long topicMessagesCount = 0;
        long transactionsCount = 0;

        try (Connection connection = dataSource.getConnection()) {
            accountsCount = getTableSize(connection, "t_entities");
            balancesCount = getTableSize(connection, "account_balances");
            topicMessagesCount = getTableSize(connection, "topic_message");
            transactionsCount = getTableSize(connection, "t_transactions");
        } catch (Exception e) {
            log.error("Unable to retrieve details from database", e);
        }

        log.info("{} accounts, {} balances, {} topic messages and {} transactions were seeded", accountsCount,
                balancesCount, topicMessagesCount, transactionsCount);
        assertThat(accountsCount).isGreaterThan(0);
        assertThat(balancesCount).isGreaterThan(0);
        assertThat(topicMessagesCount).isGreaterThan(0);
        assertThat(transactionsCount).isGreaterThan(0);
    }

    private void parse(String filePath) {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "performance")
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        recordFileParser.parse();
    }

    private void setDbProperties(String host, String name, String password, int port) {
        dbProperties.setHost(host);
        dbProperties.setName(name);
        dbProperties.setPassword(password);
        dbProperties.setPort(port);
    }

    private long getTableSize(Connection connection, String table) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
        ResultSet rs = statement.executeQuery();
        rs.next();
        return rs.getLong("count");
    }
}
