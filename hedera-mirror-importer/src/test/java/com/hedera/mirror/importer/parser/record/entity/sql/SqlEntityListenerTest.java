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
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
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
import com.hedera.mirror.importer.exception.MissingFileException;
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

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SqlEntityListenerTest extends IntegrationTest {

    private final TransactionRepository transactionRepository;
    private final EntityRepository entityRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final NonFeeTransferRepository nonFeeTransferRepository;
    private final ContractResultRepository contractResultRepository;
    private final LiveHashRepository liveHashRepository;
    private final FileDataRepository fileDataRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final RecordFileRepository recordFileRepository;
    private final SqlEntityListener sqlEntityListener;
    private final SqlProperties sqlProperties;

    private String fileName = "2019-08-30T18_10_00.419072Z.rcd";
    private RecordFile recordFile;

    @BeforeEach
    final void beforeEach() {
        String newFileHash = UUID.randomUUID().toString();
        recordFile = insertRecordFileRecord(fileName, newFileHash, "fileHash0");

        sqlEntityListener.onStart(new StreamFileData(fileName, null));
    }

    @Test
    void isEnabled() {
        sqlProperties.setEnabled(false);
        assertThat(sqlEntityListener.isEnabled()).isFalse();

        sqlProperties.setEnabled(true);
        assertThat(sqlEntityListener.isEnabled()).isTrue();
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
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer1, cryptoTransfer1.getId());
        assertExistsAndEquals(cryptoTransferRepository, cryptoTransfer2, cryptoTransfer2.getId());
    }

    @Test
    void onNonFeeTransfer() throws Exception {
        // given
        NonFeeTransfer nonFeeTransfer1 = new NonFeeTransfer(1L, new NonFeeTransfer.Id(1L, EntityId
                .of(0L, 0L, 1L, ACCOUNT)));
        NonFeeTransfer nonFeeTransfer2 = new NonFeeTransfer(-2L, new NonFeeTransfer.Id(2L, EntityId
                .of(0L, 0L, 2L, ACCOUNT)));

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertEquals(2, nonFeeTransferRepository.count());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer1, nonFeeTransfer1.getId());
        assertExistsAndEquals(nonFeeTransferRepository, nonFeeTransfer2, nonFeeTransfer2.getId());
    }

    @Test
    void onTopicMessage() throws Exception {
        // given
        TopicMessage topicMessage = getTopicMessage();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertEquals(1, topicMessageRepository.count());
        assertExistsAndEquals(topicMessageRepository, topicMessage, topicMessage.getConsensusTimestamp());
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

        recordFile = insertRecordFileRecord(UUID.randomUUID().toString(), null, null);
        sqlEntityListener.onStart(new StreamFileData(fileName, null));
        sqlEntityListener.onEntityId(entityId); // duplicate across files
        completeFileAndCommit();

        // then
        assertEquals(1, entityRepository.count());
        assertExistsAndEquals(entityRepository, entityId.toEntity(), 10L);
    }

    @Test
    void testRecordFile() {
        // when
        completeFileAndCommit();

        // then
        assertThat(recordFileRepository.count()).isEqualTo(1);
        assertThat(recordFileRepository.findByName(fileName)).hasSize(1);
    }

    @Test
    void testMissingFileInRecordFileRepository() {
        recordFileRepository.deleteAll();

        assertThrows(MissingFileException.class, () -> {
            sqlEntityListener.onStart(new StreamFileData(fileName, null));
        });
    }

    private <T, ID> void assertExistsAndEquals(CrudRepository<T, ID> repository, T expected, ID id) throws Exception {
        Optional<T> actual = repository.findById(id);
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    private String completeFileAndCommit() {
        sqlEntityListener.onEnd(recordFile);
        return recordFile.getFileHash();
    }

    private RecordFile insertRecordFileRecord(String filename, String fileHash, String prevHash) {
        if (fileHash == null) {
            fileHash = UUID.randomUUID().toString();
        }
        if (prevHash == null) {
            prevHash = UUID.randomUUID().toString();
        }

        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        RecordFile rf = new RecordFile(1L, 2L, null, filename, 0L, 0L, fileHash, prevHash, nodeAccountId, 0L, 0);
        recordFileRepository.save(rf);
        return rf;
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
