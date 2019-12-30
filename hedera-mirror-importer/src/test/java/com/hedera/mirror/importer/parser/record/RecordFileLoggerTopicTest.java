package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.KeyConverter;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.TopicIdConverter;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.util.Utility;

public class RecordFileLoggerTopicTest extends AbstractRecordFileLoggerTest {

    static final String TRANSACTION_MEMO = "transaction memo";
    static final int TOPIC_ENTITY_TYPE_ID = 4;
    static final String NODE_ID = "0.0.3";
    static final String TRANSACTION_ID = "0.0.9999-123456789";

    @ParameterizedTest
    @CsvSource({
            "0.0.65537, 10, 20, admin-key, submit-key, 30, '', 1000000",
            "0.0.2147483647, 9223372036854775807, 2147483647, admin-key, '', 9223372036854775807, memo, 1000001",
            "0.0.1, -9223372036854775808, -2147483648, '', '', -9223372036854775808, memo, 1000002",
            "0.0.55, 10, 20, admin-key, submit-key, 30, memo, 1000003"
    })
    void createTopicTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, long expirationTimeSeconds,
                         int expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                         @ConvertWith(KeyConverter.class) Key submitKey, long validStartTime, String memo,
                         long consensusTimestamp) throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(expirationTimeSeconds, expirationTimeNanos, adminKey,
                submitKey, validStartTime, memo);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);
        var expectedEntity = createTopicEntity(topicId, expirationTimeSeconds, expirationTimeNanos, adminKey, submitKey,
                validStartTime, memo);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(expectedEntity, "id");
    }

    @Test
    void createTopicTestNulls() throws Exception {
        var topicId = 200L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(10, 20, null, null,
                30, null);
        var transactionRecord = createTransactionRecord(TopicID.newBuilder().setTopicNum(topicId)
                .build(), null, null, consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository.findByPrimaryKey(0L, 0L, topicId).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .returns("".getBytes(), from(Entities::getKey))
                .returns("".getBytes(), from(Entities::getSubmitKey))
                .returns("", from(Entities::getMemo))
                .returns(false, from(Entities::isDeleted))
                .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
    }

    @Test
    void createTopicTestFiltered() throws Exception {
        var topicId = 999L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(10, 20, null, null,
                30, null);
        var transactionRecord = createTransactionRecord(TopicID.newBuilder().setTopicNum(topicId)
                .build(), null, null, consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertEquals(0L, entityRepository.count());
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void createTopicTestError() throws Exception {
        var consensusTimestamp = 3_000_000L;
        var responseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
        var transaction = createCreateTopicTransaction(10, 20, null, null,
                30, "memo");
        var transactionRecord = createTransactionRecord(null, null, null, consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertTransactionInRepository(responseCode, consensusTimestamp, null);
        assertEquals(2L, entityRepository.count()); // Node, payer, no topic
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1300, 10, 20, admin-key, submit-key, 30, memo,   11, 21, updated-admin-key, updated-submit-key, 31, " +
                    "updated-memo, 4000000",
            "0.0.1301, 10, 20, admin-key, submit-key, 30, memo,   11, 21, '', '', 31, '', 4000001",
            "0.0.1302, 0, 0, '', '', 0, '',   11, 21, updated-admin-key, updated-submit-key, 31, updated-memo, 4000002"
    })
    void updateTopicTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, long expirationTimeSeconds,
                         int expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                         @ConvertWith(KeyConverter.class) Key submitKey, long validStartTime, String memo,
                         long updatedExpirationTimeSeconds, int updatedExpirationTimeNanos,
                         @ConvertWith(KeyConverter.class) Key updatedAdminKey,
                         @ConvertWith(KeyConverter.class) Key updatedSubmitKey, long updatedValidStartTime,
                         String updatedMemo, long consensusTimestamp) throws Exception {
        // Store topic to be updated.
        var topic = createTopicEntity(topicId, expirationTimeSeconds, expirationTimeNanos, adminKey, submitKey,
                validStartTime, memo);
        entityRepository.save(topic);

        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createUpdateTopicTransaction(topicId, updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos, updatedAdminKey, updatedSubmitKey, updatedValidStartTime, updatedMemo);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);
        var expectedEntity = createTopicEntity(topicId, updatedExpirationTimeSeconds, updatedExpirationTimeNanos,
                updatedAdminKey, updatedSubmitKey,
                updatedValidStartTime, updatedMemo);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(expectedEntity, "id");
    }

    @Test
    void updateTopicTestError() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(1600).build();
        var adminKey = (Key) new KeyConverter().convert("admin-key", null);
        var submitKey = (Key) new KeyConverter().convert("submit-key", null);
        var updatedAdminKey = (Key) new KeyConverter().convert("updated-admin-key", null);
        var updatedSubmitKey = (Key) new KeyConverter().convert("updated-submit-key", null);
        var consensusTimestamp = 6_000_000L;
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;

        // Store topic to be updated.
        var topic = createTopicEntity(topicId, 10L, 20, adminKey, submitKey, 30L, "memo");
        entityRepository.save(topic);

        var transaction = createUpdateTopicTransaction(topicId, 11L, 21, updatedAdminKey, updatedSubmitKey, 31L,
                "updated-memo");
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @Test
    void updateTopicTestTopicNotFound() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(1800).build();
        var adminKey = (Key) new KeyConverter().convert("updated-admin-key", null);
        var submitKey = (Key) new KeyConverter().convert("updated-submit-key", null);
        var consensusTimestamp = 6_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;

        var topic = createTopicEntity(topicId, 11L, 21, adminKey, submitKey, 31L, "updated-memo");
        // Topic does not get stored in the repository beforehand.

        var transaction = createUpdateTopicTransaction(topicId, topic.getExpiryTimeSeconds(), topic.getExpiryTimeNanos()
                .intValue(), adminKey, submitKey, topic.getTopicValidStartTime(), topic.getMemo());
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1500, 10, 20, admin-key, submit-key, 30, memo, 5000000,   0, 0, , , 0,",
            "0.0.1500, 10, 20, admin-key, submit-key, 30, memo, 5000000,   , , , , ,",
            "0.0.1501, 0, 0, '', '', 0, '', 5000001,   0, 0, , , 0,",
            "0.0.1502, , , admin-key, submit-key, 30, memo, 5000002,   10, 20, updated-admin-key, " +
                    "updated-submit-key, 0, updated-memo",
            "0.0.1503, 10, 20, admin-key, submit-key, 30, memo, 5000003,   11, 21, , , 31,"
    })
    void updateTopicTestPartialUpdates(@ConvertWith(TopicIdConverter.class) TopicID topicId, Long expirationTimeSeconds,
                                       Integer expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                                       @ConvertWith(KeyConverter.class) Key submitKey, Long validStartTime, String memo,
                                       long consensusTimestamp, Long updatedExpirationTimeSeconds,
                                       Integer updatedExpirationTimeNanos,
                                       @ConvertWith(KeyConverter.class) Key updatedAdminKey,
                                       @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
                                       Long updatedValidStartTime, String updatedMemo) throws Exception {
        // Store topic to be updated.
        var topic = createTopicEntity(topicId, expirationTimeSeconds, expirationTimeNanos, adminKey, submitKey,
                validStartTime, memo);
        entityRepository.save(topic);

        // Setup the expected entity.
        if (updatedExpirationTimeSeconds != null && updatedExpirationTimeNanos != null) {
            topic.setExpiryTimeNs(Utility.convertToNanosMax(updatedExpirationTimeSeconds, updatedExpirationTimeNanos));
            topic.setExpiryTimeSeconds(updatedExpirationTimeSeconds);
            topic.setExpiryTimeNanos((long) updatedExpirationTimeNanos);
        }
        if (updatedValidStartTime != null) {
            topic.setTopicValidStartTime(updatedValidStartTime);
        }
        if (updatedAdminKey != null) {
            topic.setKey(updatedAdminKey.toByteArray());
        }
        if (updatedSubmitKey != null) {
            topic.setSubmitKey(updatedSubmitKey.toByteArray());
        }
        if (updatedMemo != null) {
            topic.setMemo(updatedMemo);
        }

        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createUpdateTopicTransaction(topicId, updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos, updatedAdminKey, updatedSubmitKey, updatedValidStartTime, updatedMemo);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @Test
    void deleteTopicTest() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(1700L).build();
        var consensusTimestamp = 7_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;

        // Store topic to be deleted.
        var topic = createTopicEntity(topicId, null, null, null, null, null, null);
        entityRepository.save(topic);

        // Setup expected data
        topic.setDeleted(true);

        var transaction = createDeleteTopicTransaction(topicId);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @Test
    void deleteTopicTestTopicNotFound() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(2000L).build();
        var consensusTimestamp = 10_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;

        // Setup expected data
        var topic = createTopicEntity(topicId, null, null, null, null, null, null);
        topic.setDeleted(true);
        // Topic not saved to the repository.

        var transaction = createDeleteTopicTransaction(topicId);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @Test
    void deleteTopicTestError() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(1900L).build();
        var consensusTimestamp = 9_000_000L;
        var responseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;

        // Store topic to be deleted.
        var topic = createTopicEntity(topicId, 10L, 20, null, null, 30L, null);
        entityRepository.save(topic);

        var transaction = createDeleteTopicTransaction(topicId);
        var transactionRecord = createTransactionRecord(topicId, null, null, consensusTimestamp,
                responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();

        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.9000, test-message0, 9000000, runninghash, 1",
            "0.0.9001, '', 9000001, '', 9223372036854775807"
    })
    void submitMessageTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, String message,
                           long consensusTimestamp, String runningHash,
                           long sequenceNumber) throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;

        var topic = createTopicEntity(topicId, 10L, 20, null, null, 30L, null);
        entityRepository.save(topic);

        var topicMessage = createTopicMessage(topicId, message, sequenceNumber, runningHash, consensusTimestamp);
        var transaction = createSubmitMessageTransaction(topicId, message);
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get()
                .isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestTopicNotFound() throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;
        var topicId = (TopicID) new TopicIdConverter().convert("0.0.10000", null);
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";

        var topic = createTopicEntity(topicId, null, null, null, null, null, null);
        // Topic NOT saved in the repository.

        var topicMessage = createTopicMessage(topicId, message, sequenceNumber, runningHash, consensusTimestamp);
        var transaction = createSubmitMessageTransaction(topicId, message);
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertEquals(1L, topicMessageRepository.count());
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get()
                .isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestFiltered() throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;
        var topicId = (TopicID) new TopicIdConverter().convert("0.0.999", null);
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";

        var topic = createTopicEntity(topicId, null, null, null, null, null, null);
        var transaction = createSubmitMessageTransaction(topicId, message);
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageTestTopicError() throws Exception {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var topicId = (TopicID) new TopicIdConverter().convert("0.0.11000", null);
        var consensusTimestamp = 11_000_000L;
        var message = "message";
        var sequenceNumber = 11_000L;
        var runningHash = "running-hash";

        var topic = createTopicEntity(topicId, 10L, 20, null, null, 30L, null);
        entityRepository.save(topic);

        var transaction = createSubmitMessageTransaction(topicId, message);
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), consensusTimestamp, responseCode);

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        var entity = entityRepository
                .findByPrimaryKey(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum()).get();
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .isEqualToIgnoringGivenFields(topic, "id");
        assertEquals(0L, topicMessageRepository.count());
    }

    private com.hederahashgraph.api.proto.java.Transaction createCreateTopicTransaction(long expirationTimeSeconds,
                                                                                        int expirationTimeNanos,
                                                                                        Key adminKey, Key submitKey,
                                                                                        long validStartTime,
                                                                                        String memo) {
        var innerBody = ConsensusCreateTopicTransactionBody.newBuilder()
                .setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos))
                .setValidStartTime(TestUtils.toTimestamp(validStartTime));
        if (adminKey != null) {
            innerBody.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            innerBody.setSubmitKey(submitKey);
        }
        if (memo != null) {
            innerBody.setMemo(memo);
        }
        var body = createTransactionBody().setConsensusCreateTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
    }

    private TransactionRecord createTransactionRecord(TopicID topicId, Long topicSequenceNumber,
                                                      byte[] topicRunningHash, long consensusTimestamp,
                                                      ResponseCodeEnum responseCode) {
        var receipt = TransactionReceipt.newBuilder().setStatus(responseCode);
        if (null != topicId) {
            receipt.setTopicID(topicId);
        }
        if (null != topicSequenceNumber) {
            receipt.setTopicSequenceNumber(topicSequenceNumber);
        }
        if (null != topicRunningHash) {
            receipt.setTopicRunningHash(ByteString.copyFrom(topicRunningHash));
        }
        var transactionRecord = TransactionRecord.newBuilder().setReceipt(receipt)
                .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));
        return transactionRecord.build();
    }

    private TransactionBody.Builder createTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(10).build())
                .setNodeAccountID(TestUtils.toAccountId(NODE_ID))
                .setTransactionID(TestUtils.toTransactionId(TRANSACTION_ID))
                .setMemo(TRANSACTION_MEMO);
    }

    private Entities createTopicEntity(TopicID topicId, Long expirationTimeSeconds, Integer expirationTimeNanos,
                                       Key adminKey, Key submitKey, Long validStartTime, String memo) {
        var topic = new Entities();
        topic.setEntityShard(topicId.getShardNum());
        topic.setEntityRealm(topicId.getRealmNum());
        topic.setEntityNum(topicId.getTopicNum());
        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            topic.setExpiryTimeNs(Utility
                    .convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
            topic.setExpiryTimeSeconds(expirationTimeSeconds);
            topic.setExpiryTimeNanos(expirationTimeNanos.longValue());
        }
        if (null != adminKey) {
            topic.setKey(adminKey.toByteArray());
        }
        if (null != submitKey) {
            topic.setSubmitKey(submitKey.toByteArray());
        }
        topic.setTopicValidStartTime(validStartTime);
        topic.setMemo(memo);
        topic.setEntityTypeId(TOPIC_ENTITY_TYPE_ID);
        return topic;
    }

    private TopicMessage createTopicMessage(TopicID topicId, String message, long sequenceNumber, String runningHash,
                                            long consensusTimestamp) {
        var topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp);
        topicMessage.setRealmNum((int) topicId.getRealmNum());
        topicMessage.setTopicNum((int) topicId.getTopicNum());
        topicMessage.setMessage(message.getBytes());
        topicMessage.setSequenceNumber(sequenceNumber);
        topicMessage.setRunningHash(runningHash.getBytes());
        return topicMessage;
    }

    private com.hederahashgraph.api.proto.java.Transaction createUpdateTopicTransaction(TopicID topicId,
                                                                                        Long expirationTimeSeconds,
                                                                                        Integer expirationTimeNanos,
                                                                                        Key adminKey, Key submitKey,
                                                                                        Long validStartTime,
                                                                                        String memo) {
        var innerBody = ConsensusUpdateTopicTransactionBody.newBuilder().setTopicID(topicId);

        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            innerBody.setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos));
        }
        if (validStartTime != null) {
            innerBody.setValidStartTime(TestUtils.toTimestamp(validStartTime));
        }
        if (adminKey != null) {
            innerBody.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            innerBody.setSubmitKey(submitKey);
        }
        if (memo != null) {
            innerBody.setMemo(StringValue.of(memo));
        }
        var body = createTransactionBody().setConsensusUpdateTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createDeleteTopicTransaction(TopicID topicId) {
        var innerBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId);
        var body = createTransactionBody().setConsensusDeleteTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createSubmitMessageTransaction(TopicID topicId,
                                                                                          String message) {
        var innerBody = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(topicId)
                .setMessage(ByteString.copyFrom(message.getBytes()));
        var body = createTransactionBody().setConsensusSubmitMessage(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
    }

    private void assertTransactionInRepository(ResponseCodeEnum responseCode, long consensusTimestamp, Long entityId) {
        var transaction = transactionRepository.findById(consensusTimestamp).get();
        assertThat(transaction)
                .returns(responseCode.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (entityId != null) {
            assertThat(transaction)
                    .returns(entityId, from(Transaction::getEntityId));
        }
    }
}
