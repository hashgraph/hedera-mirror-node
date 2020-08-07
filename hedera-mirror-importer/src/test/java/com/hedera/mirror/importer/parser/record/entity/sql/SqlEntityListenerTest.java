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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
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
    protected EntityRepository entityRepository;

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

    @Resource
    private DataSource dataSource;

    private String fileName;

    @BeforeEach
    final void beforeEach() {
        fileName = UUID.randomUUID().toString();
        sqlEntityListener.onStart(new StreamFileData(fileName, null));
    }

    void completeFileAndCommit() {
        sqlEntityListener.onEnd(new RecordFile(0L, 0L, null, fileName, 0L, 0L, UUID.randomUUID().toString(), "", 0));
    }

    @Test
    void onCryptoTransferList() throws Exception {
        // given
        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, EntityId.of(0L, 0L, 1L, ACCOUNT));
        CryptoTransfer cryptoTransfer2 = new CryptoTransfer(2L, -2L, EntityId.of(0L, 0L, 2L, ACCOUNT));

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
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, 1L, EntityId.of(0L, 0L, 1L, ACCOUNT));
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(2L, -2L, EntityId.of(0L, 0L, 2L, ACCOUNT));

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
        sqlProperties.setNotifyTopicMessage(false);
        // given
        TopicMessage topicMessage = getTopicMessage();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, topicMessage, topicMessage.getConsensusTimestamp());
        sqlProperties.setNotifyTopicMessage(true);
    }

    @Test
    void onTopicMessageNotify() throws Exception {
        // given
        TopicMessage topicMessage = getTopicMessage();

        String json = SqlEntityListener.OBJECT_MAPPER.writeValueAsString(topicMessage);
        PgConnection connection = dataSource.getConnection().unwrap(PgConnection.class);
        connection.execSQLUpdate("listen topic_message");

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();
        PGNotification[] notifications = connection.getNotifications(500);

        // then
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, topicMessage, topicMessage.getConsensusTimestamp());
        assertEquals(1, notifications.length);
        assertThat(notifications)
                .extracting(PGNotification::getParameter)
                .first()
                .isEqualTo(json);
        connection.close();
    }

    @Test
    void onTopicMessageNotifyPayloadTooLong() throws Exception {
        // given
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage(RandomUtils.nextBytes(5824)); // Just exceeds 8000B
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash(Strings.toByteArray("running hash"));
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(4L);

        PgConnection connection = dataSource.getConnection().unwrap(PgConnection.class);
        connection.execSQLUpdate("listen topic_message");

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();
        PGNotification[] notifications = connection.getNotifications(500);

        // then
        assertThat(topicMessageRepository.count()).isEqualTo(1);
        assertExistsAndEquals(topicMessageRepository, topicMessage, topicMessage.getConsensusTimestamp());
        assertThat(notifications).isNull();
        connection.close();
    }

    @Test
    void verifyRollback() {
        sqlProperties.setMaxJsonPayloadSize(Integer.MAX_VALUE);
        // given
        TopicMessage topicMessage = getTopicMessage();

        TopicMessage topicMessage2 = getTopicMessage();
        topicMessage2.setConsensusTimestamp(2L);
        topicMessage2.setSequenceNumber(2L);
        topicMessage2.setMessage(RandomUtils.nextBytes(10000)); // Just exceeds 8000B

        CryptoTransfer cryptoTransfer1 = new CryptoTransfer(1L, 1L, EntityId.of(0L, 0L, 1L, ACCOUNT));

        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        sqlEntityListener.onTopicMessage(topicMessage2); // error causing submission
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onEntityId(entityId);

        assertThatThrownBy(() -> completeFileAndCommit())
                .isInstanceOf(ParserException.class)
                .extracting(Throwable::getCause)
                .isInstanceOf(SQLException.class);
        sqlEntityListener.onError();

        assertEquals(0, topicMessageRepository.count());
        assertEquals(0, cryptoTransferRepository.count());
        assertEquals(0, entityRepository.count());
    }

    @Test
    void onFileData() throws Exception {
        // given
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"), EntityId
                .of(0, 0, 111, EntityTypeEnum.FILE), TransactionTypeEnum.CONSENSUSSUBMITMESSAGE.getProtoId());

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
        ContractResult expectedContractResult = new ContractResult(15L, "function parameters".getBytes(),
                10000L, "call result".getBytes(), 10000L);

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
        LiveHash expectedLiveHash = new LiveHash(20L, "live hash".getBytes());

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
        var expectedTransaction = makeTransaction();

        // when
        sqlEntityListener.onTransaction(expectedTransaction);
        completeFileAndCommit();

        // then
        assertEquals(1, transactionRepository.count());
        assertExistsAndEquals(transactionRepository, expectedTransaction, 101L);
    }

    @Test
    void onEntityId() throws Exception {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when
        sqlEntityListener.onEntityId(entityId);
        completeFileAndCommit();

        // then
        assertEquals(1, entityRepository.count());
        assertExistsAndEquals(entityRepository, entityId.toEntity(), 10L);
    }

    @Test
    void onEntityIdDuplicates() throws Exception {
        // given
        EntityId entityId = EntityId.of(0L, 0L, 10L, ACCOUNT);

        // when:
        sqlEntityListener.onEntityId(entityId);
        sqlEntityListener.onEntityId(entityId); // duplicate within file
        completeFileAndCommit();
        fileName = UUID.randomUUID().toString();
        sqlEntityListener.onStart(new StreamFileData(fileName, null));
        sqlEntityListener.onEntityId(entityId); // duplicate across files
        completeFileAndCommit();

        // then
        assertEquals(1, entityRepository.count());
        assertExistsAndEquals(entityRepository, entityId.toEntity(), 10L);
    }

    @Test
    void onDuplicateFileReturnEmpty() {
        // given: file processed once
        completeFileAndCommit();

        // when, then
        assertThrows(DuplicateFileException.class, () -> {
            sqlEntityListener.onStart(new StreamFileData(fileName, null));
        });
    }

    @Test
    void testRecordFile() {
        // when
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.count()).isEqualTo(1);
        assertThat(recordFileRepository.findByName(fileName)).hasSize(1);
    }

    static <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    private Transaction makeTransaction() {
        EntityId entityId = EntityId.of(10, 10, 10, ACCOUNT);
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(101L);
        transaction.setEntityId(entityId);
        transaction.setNodeAccountId(entityId);
        transaction.setMemo("memo".getBytes());
        transaction.setType(14);
        transaction.setResult(22);
        transaction.setTransactionHash("transaction hash".getBytes());
        transaction.setTransactionBytes("transaction bytes".getBytes());
        transaction.setPayerAccountId(entityId);
        transaction.setValidStartNs(1L);
        transaction.setValidDurationSeconds(1L);
        transaction.setMaxFee(1L);
        transaction.setChargedTxFee(1L);
        transaction.setInitialBalance(0L);
        return transaction;
    }

    private TopicMessage getTopicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(4L);

        return topicMessage;
    }
}
