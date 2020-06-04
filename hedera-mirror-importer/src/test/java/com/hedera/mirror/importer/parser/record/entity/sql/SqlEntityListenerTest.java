package com.hedera.mirror.importer.parser.record.entity.sql;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.testcontainers.shaded.org.bouncycastle.util.Strings;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

public class SqlEntityListenerTest extends IntegrationTest {

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
    protected RecordFileRepository recordFileRepository;

    @Resource
    protected SqlEntityListener sqlEntityListener;

    @Resource
    protected SqlProperties sqlProperties;

    private String fileName;

    @BeforeEach
    final void beforeEach() {
        fileName = UUID.randomUUID().toString();
        sqlEntityListener.onStart(new StreamFileData(fileName, null));
    }

    void completeFileAndCommit() {
        sqlEntityListener
                .onEnd(new RecordFile(0L, 0L, null, fileName, 0L, 0L, UUID.randomUUID().toString(), "", 0));
    }

    @Test
    void onCryptoTransferList() throws Exception {
        // given
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, 0L, 1L);
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, 0L, 2L);

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertEquals(2, cryptoTransferRepository.count());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer1, 1L);
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer2, 2L);
    }

    @Test
    void onNonFeeTransfer() throws Exception {
        // given
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, 1L, 0L, 1L);
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(2L, -2L, 0L, 2L);

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertEquals(2, nonFeeTransferRepository.count());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer1, 1L);
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer2, 2L);
    }

    @Test
    void onTopicMessage() throws Exception {
        // given
        byte[] message = Strings.toByteArray("test message");
        byte[] runningHash = Strings.toByteArray("running hash");
        TopicMessage expectedTopicMessage = new TopicMessage(1L, message, 0, runningHash, 10L, 1001, 1);

        // when
        sqlEntityListener.onTopicMessage(expectedTopicMessage);
        completeFileAndCommit();

        // then
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, expectedTopicMessage, 1L);
    }

    @Test
    void onFileData() throws Exception {
        // given
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"));

        // when
        sqlEntityListener.onFileData(expectedFileData);
        completeFileAndCommit();

        // then
        assertEquals(1, fileDataRepository.count());
        assertExistsAndEquals(fileDataRepository, expectedFileData, 11L);
    }

    @Test
    void onContractResult() throws Exception {
        // given
        ContractResult expectedContractResult = new ContractResult(15L, Strings.toByteArray("function parameters"),
                10000L, Strings.toByteArray("call result"), 10000L);

        // when
        sqlEntityListener.onContractResult(expectedContractResult);
        completeFileAndCommit();

        // then
        assertEquals(1, contractResultRepository.count());
        assertExistsAndEquals(contractResultRepository, expectedContractResult, 15L);
    }

    @Test
    void onLiveHash() throws Exception {
        // given
        LiveHash expectedLiveHash = new LiveHash(20L, Strings.toByteArray("live hash"));

        // when
        sqlEntityListener.onLiveHash(expectedLiveHash);
        completeFileAndCommit();

        // then
        assertEquals(1, liveHashRepository.count());
        assertExistsAndEquals(liveHashRepository, expectedLiveHash, 20L);
    }

    @Test
    void onTransaction() throws Exception {
        // given
        Transaction expectedTransaction = new Transaction(101L, 0L, Strings.toByteArray("memo"), 0, 0, 1L, 1L, 1L, null,
                1L, 1L, 1L, Strings.toByteArray("transactionHash"), null);

        // when
        sqlEntityListener.onTransaction(expectedTransaction);
        completeFileAndCommit();

        // then
        assertEquals(1, transactionRepository.count());
        assertExistsAndEquals(transactionRepository, expectedTransaction, 101L);
    }

    // Test that on seeing 'batchSize' number of transactions, 'executeBatch()' is called for all PreparedStatements
    // issued by the connection.
    @Test
    void batchSize() throws Exception {
        // given
        int batchSize = 10;
        sqlProperties.setBatchSize(batchSize);

        Connection connection = mock(Connection.class);
        List<PreparedStatement> insertStatements = new ArrayList<>(); // tracks all PreparedStatements
        when(connection.prepareStatement(any())).then(ignored -> {
            PreparedStatement preparedStatement = mock(PreparedStatement.class);
            when(preparedStatement.executeBatch()).thenReturn(new int[] {});
            insertStatements.add(preparedStatement);
            return preparedStatement;
        });

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        SqlEntityListener sqlEntityListener2 =
                new SqlEntityListener(sqlProperties, dataSource, recordFileRepository);
        sqlEntityListener2.onStart(new StreamFileData(UUID.randomUUID().toString(), null)); // setup connection

        // when
        for (int i = 0; i < batchSize; i++) {
            sqlEntityListener2.onTransaction(mock(Transaction.class));
        }

        // then
        for (PreparedStatement ps : insertStatements) {
            verify(ps).executeBatch();
        }

        completeFileAndCommit();  // close connection
    }

    @Test
    void onError() {
        // when
        sqlEntityListener.onNonFeeTransfer(new NonFeeTransfer(1L, 1L, 0L, 1L));
        sqlEntityListener.onCryptoTransfer(new CryptoTransfer(2L, -2L, 0L, 2L));
        sqlEntityListener.onError();

        // then
        assertEquals(0, nonFeeTransferRepository.count());
        assertEquals(0, cryptoTransferRepository.count());
    }

    @Test
    void onDuplicateFileReturnEmpty() {
        // given: file processed once
        completeFileAndCommit();

        // when, then
        assertThrows(DuplicateFileException.class, () -> {
            sqlEntityListener.onStart(new StreamFileData(fileName, null));
        });

        sqlEntityListener.onError();  // close connection
    }

    // TODO: add test to check contents of recordFileRepo

    static <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }
}
