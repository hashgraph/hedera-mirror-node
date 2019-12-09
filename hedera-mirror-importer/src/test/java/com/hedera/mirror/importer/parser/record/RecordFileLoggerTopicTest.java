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

import com.hedera.mirror.importer.TestUtils;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;

import com.hedera.mirror.importer.util.Utility;

import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.jdbc.Sql;
import javax.annotation.Resource;

// Class manually commits so have to manually cleanup tables
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class RecordFileLoggerTopicTest extends AbstractRecordFileLoggerTest {
    static final String TRANSACTION_MEMO = "transaction memo";
    static final int TOPIC_ENTITY_TYPE_ID = 4;
    static final String NODE_ID = "0.0.3";
    static final String TRANSACTION_ID = "0.0.9999-123456789";

    @BeforeEach
    void before() throws Exception {
        assertTrue(RecordFileLogger.start());
        Assertions.assertEquals(RecordFileLogger.INIT_RESULT.OK, RecordFileLogger.initFile("TopicTest"));
        parserProperties.setPersistFiles(true);
        parserProperties.setPersistSystemFiles(true);
        parserProperties.setPersistContracts(true);
        parserProperties.setPersistCryptoTransferAmounts(true);
    }

    @AfterEach
    void after() {
        RecordFileLogger.finish();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.65537, 10, 20, admin-key, submit-key, 30, 111222333, empty, SUCCESS",
            "0.0.2147483647, 9223372036854775807, 2147483647, null, null, 9223372036854775807, 111222444, memo, " +
                    "SUCCESS",
            "0.0.1, -9223372036854775808, -2147483648, empty, empty, -9223372036854775808, 111222555, memo, SUCCESS",
            "0.0.55, 10, 20, admin-key, submit-key, 30, 111222666, memo, INVALID_TOPIC_ID"
    })
    void createTopicTest(String topicId, long expirationTimeSeconds, int expirationTimeNanos, String adminKey,
                         String submitKey, long validStartTime, long consensusTimestamp, String memo,
                         String responseCode) throws Exception {
        var tid = TestUtils.toTopicId(topicId);
        var ak = TestUtils.toKey(adminKey);
        byte[] expectedAdminKey = null;
        var sk = TestUtils.toKey(submitKey);
        byte[] expectedSubmitKey = null;
        memo = TestUtils.toStringWithNullOrEmpty(memo);
        var rc = ResponseCodeEnum.valueOf(responseCode);

        var innerBody = ConsensusCreateTopicTransactionBody.newBuilder()
                .setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos))
                .setValidStartTime(TestUtils.toTimestamp(validStartTime));
        if (ak != null) {
            innerBody.setAdminKey(ak);
            expectedAdminKey = ak.toByteArray();
        }
        if (sk != null) {
            innerBody.setSubmitKey(sk);
            expectedSubmitKey = sk.toByteArray();
        }
        innerBody.setMemo(memo);
        var body = createTransactionBody().setConsensusCreateTopic(innerBody).build();
        var transaction = com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
        var receipt = TransactionReceipt.newBuilder().setStatus(rc);
        if (ResponseCodeEnum.SUCCESS == rc) {
            receipt.setTopicID(tid);
        }
        var transactionRecord = TransactionRecord.newBuilder().setReceipt(receipt)
                .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)).build();

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        // If either key is null, the expectation is that the DB will store an empty value.
        if (null == ak) {
            expectedAdminKey = new byte[0];
        }
        if (null == sk) {
            expectedSubmitKey = new byte[0];
        }

        assertThat(transactionRepository.findById(consensusTimestamp).get())
                .returns(rc.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (ResponseCodeEnum.SUCCESS == rc) {
            assertThat(entityRepository.findByPrimaryKey(tid.getShardNum(), tid.getRealmNum(), tid.getTopicNum()).get())
                    .returns(tid.getTopicNum(), from(Entities::getEntityNum))
                    .returns(tid.getRealmNum(), from(Entities::getEntityRealm))
                    .returns(Utility.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos),
                            from(Entities::getExpiryTimeNs))
                    .returns(expirationTimeSeconds,
                            from(Entities::getExpiryTimeSeconds))
                    .returns((long) expirationTimeNanos,
                            from(Entities::getExpiryTimeNanos))
                    .returns(expectedAdminKey, from(Entities::getKey))
                    .returns(expectedSubmitKey, from(Entities::getSubmitKey))
                    .returns(validStartTime, from(Entities::getTopicValidStartTime))
                    .returns(false, from(Entities::isDeleted))
                    .returns(memo, from(Entities::getMemo))
                    .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
        } else {
            Assertions.assertFalse(entityRepository
                    .findByPrimaryKey(tid.getShardNum(), tid.getRealmNum(), tid.getTopicNum()).isPresent());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1300, 10, 20, admin-key, submit-key, 30, memo, 11, 21, updated-admin-key, updated-submit-key, 31, " +
                    "888, updated-memo, SUCCESS",
            "0.0.1301, 10, 20, admin-key, submit-key, 30, empty, 0, 0, null, null, 0, 889, updated-memo, SUCCESS",
            "0.0.1302, 10, 20, null, null, 30, empty, 0, 0, empty, empty, 0, 890, null, SUCCESS",
            "0.0.1303, 10, 20, null, empty, 30, memo, 0, 21, empty, null, 0, 891, null, SUCCESS",
            "0.0.1304, 10, 20, empty, null, 30, memo, 11, 0, empty, null, 0, 892, empty, SUCCESS",
            "0.0.1300, 10, 20, admin-key, submit-key, 30, memo, 11, 21, updated-admin-key, updated-submit-key, 31, " +
                    "893, updated-memo, INVALID_TOPIC_ID"
    })
    void updateTopicTest(String topicId, long expirationTimeSeconds, long expirationTimeNanos, String adminKey,
                         String submitKey, long validStartTime, String memo, long updatedExpirationTimeSeconds,
                         long updatedExpirationTimeNanos,
                         String updatedAdminKey, String updatedSubmitKey, long updatedValidStartTime,
                         long consensusTimestamp, String updatedMemo, String responseCode) throws Exception {
        var tid = TestUtils.toTopicId(topicId);
        memo = TestUtils.toStringWithNullOrEmpty(memo);
        updatedMemo = TestUtils.toStringWithNullOrEmpty(updatedMemo);

        // Store topic to be updated.
        var topic = new Entities();
        topic.setEntityShard(tid.getShardNum());
        topic.setEntityRealm(tid.getRealmNum());
        topic.setEntityNum(tid.getTopicNum());
        topic.setKey(TestUtils.toByteArray(adminKey));
        topic.setSubmitKey(TestUtils.toByteArray(submitKey));
        topic.setTopicValidStartTime(validStartTime);
        topic.setEntityTypeId(TOPIC_ENTITY_TYPE_ID);
        topic.setExpiryTimeSeconds(expirationTimeSeconds);
        topic.setExpiryTimeNanos(expirationTimeNanos);
        topic.setExpiryTimeNs(Utility.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
        topic.setMemo(memo);
        entityRepository.save(topic);

        var rc = ResponseCodeEnum.valueOf(responseCode);
        var updatedAk = TestUtils.toKey(updatedAdminKey);
        var updatedSk = TestUtils.toKey(updatedSubmitKey);

        var innerBody = ConsensusUpdateTopicTransactionBody.newBuilder()
                .setTopicID(tid)
                .setExpirationTime(TestUtils.toTimestamp(updatedExpirationTimeSeconds, updatedExpirationTimeNanos))
                .setValidStartTime(TestUtils.toTimestamp(updatedValidStartTime));
        if (updatedAk != null) {
            innerBody.setAdminKey(updatedAk);
        }
        if (updatedSk != null) {
            innerBody.setSubmitKey(updatedSk);
        }
        if (updatedMemo != null) {
            innerBody.setMemo(StringValue.of(updatedMemo));
        }
        var body = createTransactionBody().setConsensusUpdateTopic(innerBody).build();
        var transaction = com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
        var receipt = TransactionReceipt.newBuilder().setStatus(rc);
        if (ResponseCodeEnum.SUCCESS == rc) {
            receipt.setTopicID(tid);
        }
        var transactionRecord = TransactionRecord.newBuilder().setReceipt(receipt)
                .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)).build();

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertThat(transactionRepository.findById(consensusTimestamp).get())
                .returns(rc.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (ResponseCodeEnum.SUCCESS == rc) {
            // When 0s or nulls are passed, those fields are expected to remain unmodified.
            var expectedExpirationTimeSeconds = updatedExpirationTimeSeconds;
            var expectedExpirationTimeNanos = updatedExpirationTimeNanos;
            var expectedAdminKey = (null == updatedAk) ? TestUtils.toByteArray(adminKey) : updatedAk.toByteArray();
            var expectedSubmitKey = (null == updatedSk) ? TestUtils.toByteArray(submitKey) : updatedSk.toByteArray();
            var expectedValidStartTime = (0 == updatedValidStartTime) ? validStartTime : updatedValidStartTime;
            if ((0 == updatedExpirationTimeSeconds) && (0 == updatedExpirationTimeNanos)) {
                expectedExpirationTimeSeconds = expirationTimeSeconds;
                expectedExpirationTimeNanos = expirationTimeNanos;
            }
            var expectedExpirationTime = Utility
                    .convertToNanosMax(expectedExpirationTimeSeconds, expectedExpirationTimeNanos);
            var expectedMemo = (null == updatedMemo) ? memo : updatedMemo;

            assertThat(entityRepository.findByPrimaryKey(tid.getShardNum(), tid.getRealmNum(), tid.getTopicNum()).get())
                    .returns(tid.getTopicNum(), from(Entities::getEntityNum))
                    .returns(tid.getRealmNum(), from(Entities::getEntityRealm))
                    .returns(expectedExpirationTime, from(Entities::getExpiryTimeNs))
                    .returns(expectedExpirationTimeSeconds, from(Entities::getExpiryTimeSeconds))
                    .returns((long) expectedExpirationTimeNanos, from(Entities::getExpiryTimeNanos))
                    .returns(expectedAdminKey, from(Entities::getKey))
                    .returns(expectedSubmitKey, from(Entities::getSubmitKey))
                    .returns(expectedValidStartTime, from(Entities::getTopicValidStartTime))
                    .returns(false, from(Entities::isDeleted))
                    .returns(expectedMemo, from(Entities::getMemo))
                    .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
        } else {
            assertThat(entityRepository.findByPrimaryKey(tid.getShardNum(), tid.getRealmNum(), tid.getTopicNum()).get())
                    .returns(tid.getTopicNum(), from(Entities::getEntityNum))
                    .returns(tid.getRealmNum(), from(Entities::getEntityRealm))
                    .returns(Utility
                                    .convertToNanosMax(expirationTimeSeconds, expirationTimeNanos),
                            from(Entities::getExpiryTimeNs))
                    .returns(expirationTimeSeconds,
                            from(Entities::getExpiryTimeSeconds))
                    .returns((long) expirationTimeNanos,
                            from(Entities::getExpiryTimeNanos))
                    .returns(TestUtils.toByteArray(adminKey), from(Entities::getKey))
                    .returns(TestUtils
                            .toByteArray(submitKey), from(Entities::getSubmitKey))
                    .returns(validStartTime, from(Entities::getTopicValidStartTime))
                    .returns(false, from(Entities::isDeleted))
                    .returns(memo, from(Entities::getMemo))
                    .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1400, 20200101, SUCCESS",
            "0.0.1401, 20200102, UNAUTHORIZED"
    })
    void deleteTopicTest(String topicId, long consensusTimestamp, String responseCode) throws Exception {
        var tid = TestUtils.toTopicId(topicId);

        // Store topic to be updated.
        var topic = new Entities();
        topic.setEntityShard(tid.getShardNum());
        topic.setEntityRealm(tid.getRealmNum());
        topic.setEntityNum(tid.getTopicNum());
        topic.setEntityTypeId(TOPIC_ENTITY_TYPE_ID);
        topic = entityRepository.save(topic);
        Assertions.assertFalse(topic.isDeleted());

        var rc = ResponseCodeEnum.valueOf(responseCode);
        var innerBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(tid);
        var body = createTransactionBody().setConsensusDeleteTopic(innerBody).build();
        var transaction = com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
        var receipt = TransactionReceipt.newBuilder().setStatus(rc);
        if (ResponseCodeEnum.SUCCESS == rc) {
            receipt.setTopicID(tid);
        }
        var transactionRecord = TransactionRecord.newBuilder().setReceipt(receipt)
                .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)).build();

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertThat(transactionRepository.findById(consensusTimestamp).get())
                .returns(rc.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        assertThat(entityRepository.findByPrimaryKey(tid.getShardNum(), tid.getRealmNum(), tid.getTopicNum()).get())
                .returns((ResponseCodeEnum.SUCCESS == rc), from(Entities::isDeleted));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.12000, test-message0, 2019121900, runninghash, 9, SUCCESS",
            "0.0.12001, test-message1, 2019121900, empty, 9223372036854775807, SUCCESS",
            "0.0.12002, empty, 2019121901, null, 0, INVALID_TOPIC_MESSAGE",
            "0.0.12003, test-message3, 2019121902, null, 0, INVALID_TOPIC_ID"
    })
    void submitMessageTest(String topicId, @NotNull String message, long consensusTimestamp, String runningHash,
                           long sequenceNumber, String responseCode) throws Exception {
        var tid = TestUtils.toTopicId(topicId);
        var rc = ResponseCodeEnum.valueOf(responseCode);

        var innerBody = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(tid)
                .setMessage(ByteString.copyFrom(message.getBytes())).build();
        var body = createTransactionBody().setConsensusSubmitMessage(innerBody).build();
        var transaction = com.hederahashgraph.api.proto.java.Transaction.newBuilder().setBodyBytes(body.toByteString())
                .build();
        var receipt = TransactionReceipt.newBuilder().setStatus(rc);
        if (ResponseCodeEnum.SUCCESS == rc) {
            receipt.setTopicID(tid)
                    .setTopicRunningHash(ByteString.copyFrom(runningHash.getBytes()))
                    .setTopicSequenceNumber(sequenceNumber);
        }

        var transactionRecord = TransactionRecord.newBuilder().setReceipt(receipt)
                .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)).build();

        RecordFileLogger.storeRecord(transaction, transactionRecord);
        RecordFileLogger.completeFile("", "");

        assertThat(transactionRepository.findById(consensusTimestamp).get())
                .returns(rc.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (ResponseCodeEnum.SUCCESS == rc) {
            assertThat(topicMessageRepository.findById(consensusTimestamp).get())
                    .returns(message.getBytes(), from(TopicMessage::getMessage))
                    .returns(tid.getRealmNum(), from(TopicMessage::getRealmNum))
                    .returns(tid.getTopicNum(), from(TopicMessage::getTopicNum))
                    .returns(sequenceNumber, from(TopicMessage::getSequenceNumber))
                    .returns(runningHash.getBytes(), from(TopicMessage::getRunningHash));
        } else {
            Assertions.assertFalse(topicMessageRepository.findById(consensusTimestamp).isPresent());
        }
    }

    private TransactionBody.Builder createTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(10).build())
                .setNodeAccountID(TestUtils.toAccountId(NODE_ID))
                .setTransactionID(TestUtils.toTransactionId(TRANSACTION_ID))
                .setMemo(TRANSACTION_MEMO);
    }
}
