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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
@SpringBootTest
public abstract class PerformanceIntegrationTest {
    @Resource
    DataSource dataSource;

    Connection connection;

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFileParser recordFileParser;

    private FileCopier fileCopier;

    private StreamType streamType;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private Collection<CrudRepository<?, ?>> repositories;

    @Resource
    private ApplicationStatusRepository applicationStatusRepository;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Resource
    private TopicMessageRepository topicMessageRepository;

    @Resource
    private TransactionRepository transactionRepository;

    private static final String restoreClientImagePrefix = "gcr.io/mirrornode/hedera-mirror-node/postgres-restore" +
            "-client:";

    public static GenericContainer createRestoreContainer(String dockerImageTag, DBProperties db) {
        return new GenericContainer(restoreClientImagePrefix + dockerImageTag)
                .withEnv("DB_NAME", db.getName())
                .withEnv("DB_USER", db.getUsername())
                .withEnv("DB_PASS", db.getPassword())
                .withEnv("DB_PORT", Integer.toString(db.getPort()))
                .withNetworkMode("host")
                .withStartupCheckStrategy(
                        new IndefiniteWaitOneShotStartupCheckStrategy()
                );
    }

    void parse(String filePath) {
        streamType = parserProperties.getStreamType();
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();

        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "performance")
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        recordFileParser.parse();
    }

    void checkSeededTablesArePresent() throws SQLException {
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

        // verify select tables were populated
        verifyTableSize(entityRepository, "t_entities");
        verifyTableSize(accountBalanceRepository, "account_balances");
        verifyTableSize(topicMessageRepository, "topicmessages");
        verifyTableSize(transactionRepository, "t_transactions");
    }

    void verifyTableSize(CrudRepository<?, ?> repository, String label) throws SQLException {
        long count = repository.count();

        log.info("Table {} was populated with {} rows", label, count);
        assertThat(count).isGreaterThan(0);
    }

    void clearLastProcessedRecordHash() throws SQLException {
        log.debug("Clear LastProcessedRecordHash");
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, "");
    }
}
