package com.hedera.mirror.importer.parser.record.entity;

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
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.converter.KeyConverter;
import com.hedera.mirror.importer.converter.TopicIdConverter;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public class EntityRecordItemListenerTopicTest extends AbstractEntityRecordItemListenerTest {

    static final TopicID TOPIC_ID = TopicID.newBuilder().setTopicNum(200L).build();
    static final String TRANSACTION_MEMO = "transaction memo";
    static final String NODE_ID = "0.0.3";
    static final String TRANSACTION_ID = "0.0.9999-123456789";

    @ParameterizedTest
    @CsvSource({
            "0.0.65537, 10, 20, admin-key, submit-key, '', 1000000, 1, 30",
            "0.0.2147483647, 9223372036854775807, 2147483647, admin-key, '', memo, 1000001, 1, 30",
            "0.0.1, -9223372036854775808, -2147483648, '', '', memo, 1000002, , ,",
            "0.0.55, 10, 20, admin-key, submit-key, memo, 1000003, 1, 30"
    })
    void createTopicTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, long expirationTimeSeconds,
                         int expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                         @ConvertWith(KeyConverter.class) Key submitKey, String memo, long consensusTimestamp,
                         Long autoRenewAccountNum, Long autoRenewPeriod) throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(adminKey, submitKey, memo, autoRenewAccountNum, autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        long entityCount = autoRenewAccountNum != null ? 4 : 3; // Node, payer, topic & optionally autorenew
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(entityCount, entityRepository.count());
        var expectedEntity = createTopicEntity(topicId, null, null, adminKey, submitKey, memo, autoRenewAccountNum,
                autoRenewPeriod);
        assertThat(entity).isEqualTo(expectedEntity);
    }

    @Test
    void createTopicTestNulls() throws Exception {
        var consensusTimestamp = 2_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", null, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 2, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity)
                .returns("".getBytes(), from(Entity::getKey))
                .returns("".getBytes(), from(Entity::getSubmitKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::isDeleted))
                .returns(EntityTypeEnum.TOPIC.getId(), from(Entity::getType));
    }

    // https://github.com/hashgraph/hedera-mirror-node/issues/501
    @Test
    void createTopicTestExistingAutoRenewAccount() throws Exception {
        Long autoRenewAccountId = 100L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", autoRenewAccountId, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 1, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(4L, entityRepository.count()); // Node, payer, topic, autorenew
        assertThat(entity)
                .returns("".getBytes(), from(Entity::getKey))
                .returns("".getBytes(), from(Entity::getSubmitKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::isDeleted))
                .returns(EntityTypeEnum.TOPIC.getId(), from(Entity::getType))
                .returns(autoRenewAccountId, e -> e.getAutoRenewAccountId().getId());
    }

    @Test
    void createTopicTestFiltered() throws Exception {
        var topicId = 999L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, null, null, null);
        var transactionRecord = createTransactionRecord(TopicID.newBuilder().setTopicNum(topicId)
                .build(), null, null, 1, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        assertEquals(0L, entityRepository.count());
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void createTopicTestError() throws Exception {
        var consensusTimestamp = 3_000_000L;
        var responseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
        var transaction = createCreateTopicTransaction(null, null, "memo", null, null);
        var transactionRecord = createTransactionRecord(null, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        assertTransactionInRepository(responseCode, consensusTimestamp, null);
        assertEquals(2L, entityRepository.count()); // Node, payer, no topic
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1300, 10, 20, admin-key, submit-key, memo, 11, 21, updated-admin-key, updated-submit-key, " +
                    "updated-memo, 4000000, 1, 30",
            "0.0.1301, 10, 20, admin-key, submit-key, memo, 11, 21, '', '', '', 4000001, 1, 30",
            "0.0.1302, 0, 0, '', '', '', 11, 21, updated-admin-key, updated-submit-key, updated-memo, 4000002, ,"
    })
    void updateTopicTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, long expirationTimeSeconds,
                         int expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                         @ConvertWith(KeyConverter.class) Key submitKey, String memo,
                         long updatedExpirationTimeSeconds, int updatedExpirationTimeNanos,
                         @ConvertWith(KeyConverter.class) Key updatedAdminKey,
                         @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
                         String updatedMemo, long consensusTimestamp, Long autoRenewAccountId, Long autoRenewPeriod) {
        // Store topic to be updated.
        var topic = createTopicEntity(topicId, expirationTimeSeconds, expirationTimeNanos, adminKey, submitKey, memo,
                autoRenewAccountId, autoRenewPeriod);
        entityRepository.save(topic);

        var responseCode = ResponseCodeEnum.SUCCESS;
        var transaction = createUpdateTopicTransaction(topicId, updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos, updatedAdminKey, updatedSubmitKey, updatedMemo, autoRenewAccountId,
                autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);
        var expectedEntity = createTopicEntity(topicId, updatedExpirationTimeSeconds, updatedExpirationTimeNanos,
                updatedAdminKey, updatedSubmitKey, updatedMemo, autoRenewAccountId, autoRenewPeriod);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        long entityCount = autoRenewAccountId != null ? 4 : 3; // Node, payer, topic & optionally autorenew
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(entityCount, entityRepository.count());
        assertThat(entity).isEqualTo(expectedEntity);
    }

    @Test
    void updateTopicTestError() throws Exception {
        var topicId = TopicID.newBuilder().setTopicNum(1600).build();
        var adminKey = keyFromString("admin-key");
        var submitKey = keyFromString("submit-key");
        var updatedAdminKey = keyFromString("updated-admin-key");
        var updatedSubmitKey = keyFromString("updated-submit-key");
        var consensusTimestamp = 6_000_000L;
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;

        // Store topic to be updated.
        var topic = createTopicEntity(topicId, 10L, 20, adminKey, submitKey, "memo", null, 30L);
        entityRepository.save(topic);

        var transaction = createUpdateTopicTransaction(topicId, 11L, 21,
                updatedAdminKey, updatedSubmitKey, "updated-memo", null, 30L);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void updateTopicTestTopicNotFound() throws Exception {
        var adminKey = keyFromString("updated-admin-key");
        var submitKey = keyFromString("updated-submit-key");
        var consensusTimestamp = 6_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;
        var memo = "updated-memo";
        var autoRenewAccount = EntityId.of(0L, 0L, 1L, EntityTypeEnum.ACCOUNT);
        // Topic does not get stored in the repository beforehand.

        var transaction = createUpdateTopicTransaction(TOPIC_ID, 11L, 0, adminKey, submitKey, memo,
                autoRenewAccount.getId(), 30L);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));
        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(4L, entityRepository.count()); // Node, payer, topic, autorenew

        var expectedTopic = createTopicEntity(TOPIC_ID, 11L, 0, adminKey, submitKey, memo, autoRenewAccount.getId(),
                30L);
        assertThat(entity).isEqualTo(expectedTopic);
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.1500, 10, 20, admin-key, submit-key, memo, 5000000, 0, 0, , , , 1, 30, , 0",
            "0.0.1500, 10, 20, admin-key, submit-key, memo, 5000000, , , , , , 1, 30, , ",
            "0.0.1501, 0, 0, '', '', '', 5000001, 0, 0, , , , , , ,",
            "0.0.1502, , , admin-key, submit-key, memo, 5000002, 10, 20, updated-admin-key, updated-submit-key, " +
                    "updated-memo, 1, 30, 11, 31",
            "0.0.1503, , , , , '', 5000003, 11, 21, admin-key, submit-key, memo, , , 1, 30"
    })
    void updateTopicTestPartialUpdates(@ConvertWith(TopicIdConverter.class) TopicID topicId, Long expirationTimeSeconds,
                                       Integer expirationTimeNanos, @ConvertWith(KeyConverter.class) Key adminKey,
                                       @ConvertWith(KeyConverter.class) Key submitKey, String memo,
                                       long consensusTimestamp, Long updatedExpirationTimeSeconds,
                                       Integer updatedExpirationTimeNanos,
                                       @ConvertWith(KeyConverter.class) Key updatedAdminKey,
                                       @ConvertWith(KeyConverter.class) Key updatedSubmitKey, String updatedMemo,
                                       Long autoRenewAccountNum, Long autoRenewPeriod, Long updatedAutoRenewAccountNum,
                                       Long updatedAutoRenewPeriod) {
        // Store topic to be updated.
        var topic = createTopicEntity(topicId, expirationTimeSeconds, expirationTimeNanos, adminKey, submitKey, memo,
                autoRenewAccountNum, autoRenewPeriod);
        entityRepository.save(topic);

        if (updatedAutoRenewPeriod != null) {
            topic.setAutoRenewPeriod(updatedAutoRenewPeriod);
        }
        if (updatedExpirationTimeSeconds != null && updatedExpirationTimeNanos != null) {
            topic.setExpirationTimestamp(Utility
                    .convertToNanosMax(updatedExpirationTimeSeconds, updatedExpirationTimeNanos));
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
                updatedExpirationTimeNanos, updatedAdminKey, updatedSubmitKey, updatedMemo, updatedAutoRenewAccountNum,
                updatedAutoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        long entityCount = 3;
        if (autoRenewAccountNum != null) {
            ++entityCount;
        }
        if (updatedAutoRenewAccountNum != null) {
            ++entityCount;
            topic.setAutoRenewAccountId(EntityId.of(0L, 0L, updatedAutoRenewAccountNum, EntityTypeEnum.ACCOUNT));
        }
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(entityCount, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTest() throws Exception {
        var consensusTimestamp = 7_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        entityRepository.save(topic);

        // Setup expected data
        topic.setDeleted(true);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestTopicNotFound() throws Exception {
        var consensusTimestamp = 10_000_000L;
        var responseCode = ResponseCodeEnum.SUCCESS;

        // Setup expected data
        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        topic.setDeleted(true);
        // Topic not saved to the repository.

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestError() throws Exception {
        var consensusTimestamp = 9_000_000L;
        var responseCode = ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, 10L, 20, null, null, "", null, null);
        entityRepository.save(topic);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity).isEqualTo(topic);
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.9000, test-message0, 9000000, runninghash, 1, 1, , , , ",
            "0.0.9001, test-message1, 9000001, runninghash1, 9223372036854775807, 2, 1, 1, 7, 89999999",
            "0.0.9001, test-message2, 9000001, runninghash2, 9223372036854775807, 2, 2, 4, 7, 89999999",
            "0.0.9001, test-message3, 9000001, runninghash3, 9223372036854775807, 2, 4, 4, 7, 89999999",
    })
    void submitMessageTest(@ConvertWith(TopicIdConverter.class) TopicID topicId, String message,
                           long consensusTimestamp, String runningHash, long sequenceNumber, int runningHashVersion,
                           Integer chunkNum, Integer chunkTotal, Long payerAccountIdNum, Long validStartNs) throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;

        var topic = createTopicEntity(topicId, 10L, 20, null, null, "", null, null);
        entityRepository.save(topic);

        var topicMessage = createTopicMessage(topicId, message, sequenceNumber, runningHash, consensusTimestamp,
                runningHashVersion, chunkNum, chunkTotal, payerAccountIdNum, validStartNs);
        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, payerAccountIdNum,
                TestUtils.toTimestamp(validStartNs));
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertThat(entity).isEqualTo(topic);
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get()
                .isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestTopicNotFound() throws Exception {
        var responseCode = ResponseCodeEnum.SUCCESS;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var payerAccountIdNum = 6L;
        var validStartNs = 7L;

        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        // Topic NOT saved in the repository.

        var topicMessage = createTopicMessage(TOPIC_ID, message, sequenceNumber, runningHash, consensusTimestamp,
                runningHashVersion, chunkNum, chunkTotal, payerAccountIdNum, validStartNs);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, payerAccountIdNum,
                TestUtils.toTimestamp(validStartNs));
        var transactionRecord = createTransactionRecord(TOPIC_ID, sequenceNumber, runningHash
                .getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(3L, entityRepository.count()); // Node, payer, topic
        assertEquals(1L, topicMessageRepository.count());
        assertThat(entity).isEqualTo(topic);
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get()
                .isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestFiltered() throws Exception {
        // given
        var responseCode = ResponseCodeEnum.SUCCESS;
        var topicId = (TopicID) new TopicIdConverter().convert("0.0.999", null); // excluded in application-default.yml
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 1;
        var chunkNum = 3;
        var chunkTotal = 5;
        var payerAccountIdNum = 6L;
        var validStartNs = 7L;

        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, payerAccountIdNum,
                TestUtils.toTimestamp(validStartNs));
        var transactionRecord = createTransactionRecord(topicId, sequenceNumber, runningHash
                .getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        // when
        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        // then
        // if the transaction is filtered out, nothing in it should affect the state
        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageTestInvalidChunkInfo() throws Exception {
        // given
        var id = 10_000_000L;
        var topicId = TopicID.newBuilder().setTopicNum(9000).build();
        var transaction = createSubmitMessageTransaction(topicId, "message", 3, 5, null,
                TestUtils.toTimestamp(Long.MAX_VALUE, Integer.MAX_VALUE));
        var transactionRecord = createTransactionRecord(topicId, 10_000L, "running-hash"
                .getBytes(), 2, id, ResponseCodeEnum.SUCCESS);

        // when
        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        // then
        assertEquals(3L, entityRepository.count());
        assertEquals(1L, topicMessageRepository.count());
        assertThat(topicMessageRepository.findById(id))
                .get()
                .extracting(TopicMessage::getValidStartTimestamp, TopicMessage::getPayerAccountId)
                .containsExactly(Long.MAX_VALUE, null);
    }

    @Test
    void submitMessageTestTopicError() throws Exception {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var consensusTimestamp = 11_000_000L;
        var message = "message";
        var sequenceNumber = 11_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;

        var topic = createTopicEntity(TOPIC_ID, 10L, 20, null, null, "", null, null);
        entityRepository.save(topic);

        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, PAYER.getAccountNum(),
                TestUtils.toTimestamp(validStartNs));
        var transactionRecord = createTransactionRecord(TOPIC_ID, sequenceNumber, runningHash.getBytes(),
                runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        AccountID payerAccountID = TestUtils.toTransactionId(TRANSACTION_ID).getAccountID();
        assertEntities(entity.toEntityId(), EntityId.of(payerAccountID), EntityId.of(NODE));
        assertThat(entity).isEqualTo(topic);
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageToInvalidTopicEntityNotCreated() {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var payerAccountIdNum = 6L;
        var validStartNs = 7L;

        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        // Topic NOT saved in the repository.

        createTopicMessage(TOPIC_ID, message, sequenceNumber, runningHash, consensusTimestamp,
                runningHashVersion, chunkNum, chunkTotal, payerAccountIdNum, validStartNs);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, payerAccountIdNum,
                TestUtils.toTimestamp(validStartNs));
        var transactionRecord = createTransactionRecord(TOPIC_ID, sequenceNumber, runningHash
                .getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(new RecordItem(transaction, transactionRecord));

        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        AccountID payerAccountID = TestUtils.toTransactionId(TRANSACTION_ID).getAccountID();
        assertEntities(EntityId.of(payerAccountID), EntityId.of(NODE));
        assertEquals(0, topicMessageRepository.count());
    }

    private com.hederahashgraph.api.proto.java.Transaction createCreateTopicTransaction(
            Key adminKey, Key submitKey, String memo, Long autoRenewAccount, Long autoRenewPeriod) {
        var innerBody = ConsensusCreateTopicTransactionBody.newBuilder();
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(AccountID.newBuilder().setAccountNum(autoRenewAccount).build());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod).build());
        }
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
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build()
                        .toByteString())
                .build();
    }

    private TransactionRecord createTransactionRecord(TopicID topicId, long consensusTimestamp,
                                                      ResponseCodeEnum responseCode) {
        return createTransactionRecord(topicId, 10_000L, "running_hash"
                .getBytes(), 1, consensusTimestamp, responseCode);
    }

    private TransactionRecord createTransactionRecord(TopicID topicId, Long topicSequenceNumber,
                                                      byte[] topicRunningHash, int runningHashVersion,
                                                      long consensusTimestamp, ResponseCodeEnum responseCode) {
        var receipt = TransactionReceipt.newBuilder().setStatus(responseCode);
        if (null != topicId) {
            receipt.setTopicID(topicId);
        }
        if (null != topicSequenceNumber) {
            receipt.setTopicSequenceNumber(topicSequenceNumber);
        }
        if (null != topicRunningHash) {
            receipt.setTopicRunningHash(ByteString.copyFrom(topicRunningHash));
            receipt.setTopicRunningHashVersion(runningHashVersion);
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

    private Entity createTopicEntity(TopicID topicId, Long expirationTimeSeconds, Integer expirationTimeNanos,
                                     Key adminKey, Key submitKey, String memo, Long autoRenewAccountNum,
                                     Long autoRenewPeriod) {
        var topic = EntityId.of(topicId).toEntity();
        if (autoRenewAccountNum != null) {
            var autoRenewAccount = EntityId.of(0L, 0L, autoRenewAccountNum, EntityTypeEnum.ACCOUNT);
            entityRepository.findById(autoRenewAccount.getId())
                    .orElse(entityRepository.save(autoRenewAccount.toEntity()));
            topic.setAutoRenewAccountId(autoRenewAccount);
        }
        if (autoRenewPeriod != null) {
            topic.setAutoRenewPeriod(autoRenewPeriod);
        }
        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            topic.setExpirationTimestamp(Utility.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
        }
        if (null != adminKey) {
            topic.setKey(adminKey.toByteArray());
        }
        if (null != submitKey) {
            topic.setSubmitKey(submitKey.toByteArray());
        }
        topic.setMemo(memo);
        topic.setType(EntityTypeEnum.TOPIC.getId());
        return topic;
    }

    private TopicMessage createTopicMessage(TopicID topicId, String message, long sequenceNumber, String runningHash,
                                            long consensusTimestamp, int runningHashVersion, Integer chunkNum,
                                            Integer chunkTotal, Long payerAccountIdNum, Long validStartNs) {

        var topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp);
        topicMessage.setRealmNum((int) topicId.getRealmNum());
        topicMessage.setTopicNum((int) topicId.getTopicNum());
        topicMessage.setMessage(message.getBytes());
        topicMessage.setSequenceNumber(sequenceNumber);
        topicMessage.setRunningHash(runningHash.getBytes());
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setChunkNum(chunkNum);
        topicMessage.setChunkTotal(chunkTotal);
        topicMessage.setValidStartTimestamp(validStartNs);

        EntityId payerAccountEntityId = payerAccountIdNum == null ? null : EntityId
                .of(AccountID.newBuilder().setAccountNum(payerAccountIdNum).build());
        topicMessage.setPayerAccountId(payerAccountEntityId);

        return topicMessage;
    }

    private com.hederahashgraph.api.proto.java.Transaction createUpdateTopicTransaction(
            TopicID topicId, Long expirationTimeSeconds, Integer expirationTimeNanos, Key adminKey, Key submitKey,
            String memo, Long autoRenewAccount, Long autoRenewPeriod) {
        var innerBody = ConsensusUpdateTopicTransactionBody.newBuilder().setTopicID(topicId);

        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            innerBody.setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos));
        }
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(AccountID.newBuilder().setShardNum(topicId.getShardNum())
                    .setRealmNum(topicId.getRealmNum()).setAccountNum(autoRenewAccount).build());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod).build());
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
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createDeleteTopicTransaction(TopicID topicId) {
        var innerBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId);
        var body = createTransactionBody().setConsensusDeleteTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createSubmitMessageTransaction(
            TopicID topicId, String message, Integer chunkNum, Integer chunkTotal, Long payerAccountIdNum,
            Timestamp validStartNs) {
        var submitMessageTransactionBodyBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(topicId)
                .setMessage(ByteString.copyFrom(message.getBytes()));

        if (chunkNum != null) {
            TransactionID.Builder transactionId = TransactionID.newBuilder();
            if (payerAccountIdNum != null) {
                transactionId.setAccountID(AccountID.newBuilder().setAccountNum(payerAccountIdNum).build());
            }
            if (validStartNs != null) {
                transactionId.setTransactionValidStart(validStartNs);
            }
            submitMessageTransactionBodyBuilder
                    .setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                            .setNumber(chunkNum)
                            .setTotal(chunkTotal)
                            .setInitialTransactionID(transactionId));
        }

        var innerBody = submitMessageTransactionBodyBuilder.build();

        var body = createTransactionBody().setConsensusSubmitMessage(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build()
                        .toByteString())
                .build();
    }

    private Entity getTopicEntity(TopicID topicId) {
        return getEntity(EntityId.of(topicId).getId());
    }
}
