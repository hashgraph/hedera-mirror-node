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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@Log4j2
@SpringBootTest
public abstract class PerformanceIntegrationTest {
    @Resource
    DataSource dataSource;

    Connection connection;

    @Resource
    private ApplicationStatusRepository applicationStatusRepository;

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

    void checkSeededTablesArePresent() {
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

    long getTableSize(String table) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("select count (*) from " + table);
        ResultSet rs = statement.executeQuery();
        rs.next();
        return rs.getLong("count");
    }

    void getAccounts(long pageSize) throws SQLException {
        String sqlQuery = "select ab.balance as account_balance\n" +
                "    , ab.consensus_timestamp as consensus_timestamp\n" +
                "    , 0 as entity_shard\n" +
                "    , coalesce(ab.account_realm_num, e.entity_realm) as entity_realm\n" +
                "    , coalesce(ab.account_num, e.entity_num) as entity_num\n" +
                "    , e.exp_time_ns\n" +
                "    , e.auto_renew_period\n" +
                "    , e.key\n" +
                "    , e.deleted\n" +
                "from account_balances ab\n" +
                "full outer join t_entities e\n" +
                "    on (0 = e.entity_shard\n" +
                "        and ab.account_realm_num = e.entity_realm\n" +
                "        and ab.account_num =  e.entity_num\n" +
                "        and e.fk_entity_type_id < 3)\n" +
                "where ab.consensus_timestamp = (select max(consensus_timestamp) from account_balances)\n" +
                "    and \n" +
                "1=1 and 1=1 and 1=1 order by coalesce(ab.account_num, e.entity_num) desc\n" +
                "limit ?";
        runSelectStatementWithPageSize(sqlQuery, pageSize);
    }

    void getBalances(long pageSize) throws SQLException {
        String sqlQuery = "select ab.consensus_timestamp,\n" +
                "ab.account_realm_num as realm_num, ab.account_num as entity_num, ab.balance\n" +
                " from account_balances ab\n" +
                " where  consensus_timestamp = (select consensus_timestamp from account_balances ab\n" +
                " where\n" +
                "((ab.consensus_timestamp  >=  0) )\n" +
                "order by consensus_timestamp desc limit 1)\n" +
                " and\n" +
                "1=1 and 1=1 and 1=1 order by consensus_timestamp desc, account_realm_num desc,account_num desc limit" +
                " ?";
        runSelectStatementWithPageSize(sqlQuery, pageSize);
    }

    void getTopicMessages(long pageSize) throws SQLException {
        String sqlQuery = "select consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number\n" +
                "from topic_message limit ?;";

        runSelectStatementWithPageSize(sqlQuery, pageSize);
    }

    void getTransactions(long pageSize) throws SQLException {
        String sqlQuery = "select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n" +
                "   , t.memo\n" +
                "\t, t.consensus_ns\n" +
                "   , valid_start_ns\n" +
                "   , coalesce(ttr.result, 'UNKNOWN') as result\n" +
                "   , coalesce(ttt.name, 'UNKNOWN') as name\n" +
                "   , t.fk_node_acc_id\n" +
                "   , enode.entity_realm as node_realm\n" +
                "   , enode.entity_num as node_num\n" +
                "   , ctl.realm_num as account_realm\n" +
                "   , ctl.entity_num as account_num\n" +
                "   , amount\n" +
                "   , t.charged_tx_fee\n" +
                "   , t.valid_duration_seconds\n" +
                "   , t.max_fee\n" +
                " from (      select distinct ctl.consensus_timestamp\n" +
                "       from t_cryptotransferlists ctl\n" +
                "       join t_transactions t on t.consensus_ns = ctl.consensus_timestamp\n" +
                "       where 1=1\n" +
                "and ((t.consensus_ns  >=  0) ) and 1=1   order by ctl.consensus_timestamp desc\n" +
                "limit ? ) as tlist\n" +
                "   join t_transactions t on tlist.consensus_timestamp = t.consensus_ns\n" +
                "   left outer join t_transaction_results ttr on ttr.proto_id = t.result\n" +
                "   join t_entities enode on enode.id = t.fk_node_acc_id\n" +
                "   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n" +
                "   left outer join t_transaction_types ttt on ttt.proto_id = t.type\n" +
                "   left outer join t_cryptotransferlists ctl on  tlist.consensus_timestamp = ctl" +
                ".consensus_timestamp\n" +
                "   order by t.consensus_ns desc, account_num asc, amount asc;";
        runSelectStatementWithPageSize(sqlQuery, pageSize);
    }

    void runSelectStatementWithPageSize(String sqlQuery, long pageSize) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sqlQuery,
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY);
        statement.setLong(1, pageSize);
        ResultSet rs = statement.executeQuery();
        rs.last();
        assertThat(rs.getRow()).isEqualTo(pageSize);
    }

    void verifyTableSize(String table, String label) throws SQLException {
        long count = getTableSize(table);

        log.info("{} {} were seeded", count, label);
        assertThat(count).isGreaterThan(0);
    }

    void clearLastProcessedRecordHash() throws SQLException {
        log.debug("Clear LastProcessedRecordHash");
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, "");
    }
}
