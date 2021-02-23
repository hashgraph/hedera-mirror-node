package com.hedera.mirror.importer.parser.performance;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.data.repository.CrudRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;

import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
@SpringBootTest
public abstract class PerformanceIntegrationTest {

    @Value("classpath:data/recordstreams/performance/v2")
    Resource[] testFiles;

    @Autowired
    private RecordFileReader recordFileReader;

    @Autowired
    private RecordFileParser recordFileParser;

    @Autowired
    DataSource dataSource;

    Connection connection;

    @Autowired
    private DBProperties dbProperties;

    @Autowired
    private RecordFileRepository recordFileRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Autowired
    private TopicMessageRepository topicMessageRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String restoreClientImagePrefix = "gcr.io/mirrornode/hedera-mirror-node/postgres-restore" +
            "-client:";

    protected GenericContainer createRestoreContainer(String dockerImageTag) {
        log.debug("Creating restore container to connect to {}", dbProperties);
        return new GenericContainer(restoreClientImagePrefix + dockerImageTag)
                .withEnv("DB_NAME", dbProperties.getName())
                .withEnv("DB_USER", dbProperties.getUsername())
                .withEnv("DB_PASS", dbProperties.getPassword())
                .withEnv("DB_PORT", Integer.toString(dbProperties.getPort()))
                .withNetworkMode("host")
                .withStartupCheckStrategy(
                        new IndefiniteWaitOneShotStartupCheckStrategy()
                );
    }

    void parse() throws Exception {
        for (Resource resource : testFiles) {
            RecordFile recordFile = recordFileReader.read(StreamFileData.from(resource.getFile()));
            recordFileParser.parse(recordFile);
        }
    }

    void checkSeededTablesArePresent() throws SQLException {
        String[] tables = new String[] {"account_balance_sets", "account_balance", "flyway_schema_history",
                "non_fee_transfer", "contract_result", "crypto_transfer",
                "t_entities", "t_entity_types", "file_data", "live_hash", "record_file",
                "t_transaction_results",
                "t_transaction_types", "transaction", "topic_message"
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

        // verify all expected tables are present
        Collections.sort(discoveredTables);
        log.info("Encountered tables: {}", discoveredTables);
        assertThat(discoveredTables).isEqualTo(Arrays.asList(tables));

        // verify select tables were populated
        verifyTableSize(entityRepository, "t_entities");
        verifyTableSize(accountBalanceRepository, "account_balance");
        verifyTableSize(topicMessageRepository, "topicmessages");
        verifyTableSize(transactionRepository, "transaction");
    }

    void verifyTableSize(CrudRepository<?, ?> repository, String label) throws SQLException {
        long count = repository.count();

        log.info("Table {} was populated with {} rows", label, count);
        assertThat(count).isGreaterThan(0);
    }

    void clearLastProcessedRecordHash() {
        recordFileRepository.findLatest().ifPresent(r -> {
            r.setHash("");
            recordFileRepository.save(r);
        });
    }
}
