package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.shaded.org.bouncycastle.util.Strings;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.DatabaseUtilities;

@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class PostgresWritingRecordParserItemHandlerTest extends IntegrationTest {

    @Resource
    protected TransactionRepository transactionRepository;

    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;

    @Resource
    protected NonFeeTransferRepository nonFeeTransferRepository;

    @Resource
    protected ContractResultRepository contractResultRepository;

    @Resource
    protected LiveHashRepository liveHashRepository;

    @Resource
    protected FileDataRepository fileDataRepository;

    @Resource
    protected TopicMessageRepository topicMessageRepository;

    @Resource
    protected PostgresWritingRecordParsedItemHandler postgresWriter;

    @Resource
    protected PostgresWriterProperties postgresWriterProperties;

    protected Connection connection;

    @BeforeEach
    final void beforeEach() throws Exception {
        connection = DatabaseUtilities.getConnection();
        connection.setAutoCommit(false);
        postgresWriter.initSqlStatements(connection);
    }

    @AfterEach
    final void afterEach() throws Exception {
        postgresWriter.finish();
        connection.close();
    }

    void completeFileAndCommit() throws Exception {
        postgresWriter.onFileComplete();
        connection.commit();
    }

    @Test
    void onCryptoTransferList() throws Exception {
        // setup
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, 0L, 1L);
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, 0L, 2L);

        // when
        postgresWriter.onCryptoTransferList(cryptoTransfer1);
        postgresWriter.onCryptoTransferList(cryptoTransfer2);
        completeFileAndCommit();

        // expect
        assertEquals(2, cryptoTransferRepository.count());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer1, 1L);
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer2, 2L);
    }

    @Test
    void onNonFeeTransfer() throws Exception {
        // setup
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, 1L, 0L, 1L);
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(2L, -2L, 0L, 2L);

        // when
        postgresWriter.onNonFeeTransfer(nonFeeTransfer1);
        postgresWriter.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // expect
        assertEquals(2, nonFeeTransferRepository.count());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer1, 1L);
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer2, 2L);
    }

    @Test
    void onTopicMessage() throws Exception {
        // setup
        byte[] message = Strings.toByteArray("test message");
        byte[] runningHash = Strings.toByteArray("running hash");
        TopicMessage expectedTopicMessage = new TopicMessage(1L, message, 0, runningHash, 10L, 1001);

        // when
        postgresWriter.onTopicMessage(expectedTopicMessage);
        completeFileAndCommit();

        // expect
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, expectedTopicMessage, 1L);
    }

    @Test
    void onFileData() throws Exception {
        // setup
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"));

        // when
        postgresWriter.onFileData(expectedFileData);
        completeFileAndCommit();

        // expect
        assertEquals(1, fileDataRepository.count());
        assertExistsAndEquals(fileDataRepository, expectedFileData, 11L);
    }

    @Test
    void onContractResult() throws Exception {
        // setup
        ContractResult expectedContractResult = new ContractResult(15L, Strings.toByteArray("function parameters"),
                10000L, Strings.toByteArray("call result"), 10000L);

        // when
        postgresWriter.onContractResult(expectedContractResult);
        completeFileAndCommit();

        // expect
        assertEquals(1, contractResultRepository.count());
        assertExistsAndEquals(contractResultRepository, expectedContractResult, 15L);
    }

    @Test
    void onLiveHash() throws Exception {
        // setup
        LiveHash expectedLiveHash = new LiveHash(20L, Strings.toByteArray("live hash"));

        // when
        postgresWriter.onLiveHash(expectedLiveHash);
        completeFileAndCommit();

        // expect
        assertEquals(1, liveHashRepository.count());
        assertExistsAndEquals(liveHashRepository, expectedLiveHash, 20L);
    }

    @Test
    void onTransaction() throws Exception {
        // setup
        Transaction expectedTransaction = new Transaction(101L, 0L, Strings.toByteArray("memo"), 0, 0, 1L, 1L, 1L, null,
                1L, 1L, 1L, 1L, Strings.toByteArray("transactionHash"), null);

        // when
        postgresWriter.onTransaction(expectedTransaction);
        completeFileAndCommit();

        // expect
        assertEquals(1, transactionRepository.count());
        assertExistsAndEquals(transactionRepository, expectedTransaction, 101L);
    }

    // Test that on seeing 'batchSize' number of transactions, 'executeBatch()' is called for all PreparedStatements
    // issued by the connection.
    @Test
    void batchSize() throws Exception {
        // setup
        int batchSize = 10;
        postgresWriterProperties.setBatchSize(batchSize);
        Connection connection2 = Mockito.mock(Connection.class);
        List<PreparedStatement> preparedStatements = new ArrayList<>(); // tracks all PreparedStatements
        when(connection2.prepareStatement(any())).then(ignored -> {
            PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
            when(preparedStatement.executeBatch()).thenReturn(new int[] {});
            preparedStatements.add(preparedStatement);
            return preparedStatement;
        });
        PostgresWritingRecordParsedItemHandler postgresWriter2 =
                new PostgresWritingRecordParsedItemHandler(postgresWriterProperties);
        postgresWriter2.initSqlStatements(connection2);

        // when
        for (int i = 0; i < batchSize; i++) {
            postgresWriter2.onTransaction(Mockito.mock(Transaction.class));
        }

        // expect
        for (PreparedStatement ps : preparedStatements) {
            verify(ps).executeBatch();
        }
    }

    @Test
    void rollback() throws Exception {
        // when
        postgresWriter.onNonFeeTransfer(new NonFeeTransfer(1L, 1L, 0L, 1L));
        postgresWriter.onCryptoTransferList(new CryptoTransfer(2L, -2L, 0L, 2L));
        connection.rollback();

        // expect
        assertEquals(0, nonFeeTransferRepository.count());
        assertEquals(0, cryptoTransferRepository.count());
    }

    static <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }
}
