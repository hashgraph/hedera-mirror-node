/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record.entity;

import static com.hedera.mirror.importer.TestUtils.toEntityTransactions;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.converter.KeyConverter;
import com.hedera.mirror.importer.converter.TopicIdArgumentConverter;
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
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class EntityRecordItemListenerTopicTest extends AbstractEntityRecordItemListenerTest {

    static final TopicID TOPIC_ID = TopicID.newBuilder().setTopicNum(200L).build();
    static final String TRANSACTION_MEMO = "transaction memo";
    static final String NODE_ID = "0.0.3";
    static final String TRANSACTION_ID = "0.0.9999-123456789";
    static final EntityId PAYER_ACCOUNT_ID = EntityId.of("0.0.9999", EntityType.ACCOUNT);

    @ParameterizedTest
    @CsvSource({
        "0.0.65537, admin-key, submit-key, '', 1000000, 1, 30",
        "0.0.2147483647, admin-key, '', memo, 1000001, 1, 30",
        "0.0.1, '', '', memo, 1000002, , ,",
        "0.0.55, admin-key, submit-key, memo, 1000003, 1, 30"
    })
    void createTopicTest(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            @ConvertWith(KeyConverter.class) Key adminKey,
            @ConvertWith(KeyConverter.class) Key submitKey,
            String memo,
            long consensusTimestamp,
            Long autoRenewAccountNum,
            Long autoRenewPeriod) {
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(adminKey, submitKey, memo, autoRenewAccountNum, autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);
        var recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
        // topic id should be filtered
        var entityIds = transactionRecord.getTransferList().getAccountAmountsList().stream()
                .map(aa -> EntityId.of(aa.getAccountID()))
                .collect(Collectors.toList());
        entityIds.add(EntityId.of(
                recordItem.getTransactionBody().getConsensusCreateTopic().getAutoRenewAccount()));
        entityIds.add(EntityId.of(recordItem.getTransactionBody().getNodeAccountID()));
        entityIds.add(recordItem.getPayerAccountId());
        var expectedEntityTransactions = toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values();

        parseRecordItemAndCommit(recordItem);

        var entity = getTopicEntity(topicId);

        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        var expectedEntity =
                createTopicEntity(topicId, null, null, adminKey, submitKey, memo, autoRenewAccountNum, autoRenewPeriod);
        expectedEntity.setCreatedTimestamp(consensusTimestamp);
        expectedEntity.setDeleted(false);
        expectedEntity.setTimestampLower(consensusTimestamp);

        assertThat(entity).isEqualTo(expectedEntity);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @Test
    void createTopicTestNulls() {
        var consensusTimestamp = 2_000_000L;
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", null, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 2, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity)
                .returns("".getBytes(), from(Entity::getKey))
                .returns("".getBytes(), from(Entity::getSubmitKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::getDeleted))
                .returns(EntityType.TOPIC, from(Entity::getType));
    }

    // https://github.com/hashgraph/hedera-mirror-node/issues/501
    @Test
    void createTopicTestExistingAutoRenewAccount() {
        Long autoRenewAccountId = 100L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", autoRenewAccountId, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 1, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);

        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity)
                .returns("".getBytes(), from(Entity::getKey))
                .returns("".getBytes(), from(Entity::getSubmitKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::getDeleted))
                .returns(EntityType.TOPIC, from(Entity::getType))
                .returns(autoRenewAccountId, AbstractEntity::getAutoRenewAccountId);
    }

    @Test
    void createTopicTestFiltered() {
        var topicId = 999L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, null, null, null);
        var transactionRecord = createTransactionRecord(
                TopicID.newBuilder().setTopicNum(topicId).build(), null, null, 1, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertEquals(0L, entityRepository.count());
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void createTopicTestError() {
        var consensusTimestamp = 3_000_000L;
        var responseCode = INSUFFICIENT_ACCOUNT_BALANCE;
        var transaction = createCreateTopicTransaction(null, null, "memo", null, null);
        var transactionRecord = createTransactionRecord(null, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, null);
        assertEquals(0L, entityRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
        "11, 21, updated-admin-key, updated-submit-key, updated-memo, 1, 30",
        "11, 21, '', '', '', 0, 30",
        "11, 21, updated-admin-key, updated-submit-key, updated-memo, ,"
    })
    void updateTopicTest(
            long updatedExpirationTimeSeconds,
            int updatedExpirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key updatedAdminKey,
            @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
            String updatedMemo,
            Long autoRenewAccountId,
            Long autoRenewPeriod) {
        var topic = domainBuilder
                .topic()
                .customize(t -> t.permanentRemoval(null).obtainerId(null))
                .persist();
        var updateTimestamp = topic.getCreatedTimestamp() + 100L;

        var topicId = TopicID.newBuilder().setTopicNum(topic.getNum()).build();
        var transaction = createUpdateTopicTransaction(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedAdminKey,
                updatedSubmitKey,
                updatedMemo,
                autoRenewAccountId,
                autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, updateTimestamp, SUCCESS);

        var expectedAutoRenewAccountId =
                autoRenewAccountId == null ? topic.getAutoRenewAccountId() : autoRenewAccountId;
        var expectedAutoRenewPeriod = autoRenewPeriod == null ? topic.getAutoRenewPeriod() : autoRenewPeriod;
        var expected = createTopicEntity(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedAdminKey,
                updatedSubmitKey,
                updatedMemo,
                expectedAutoRenewAccountId,
                expectedAutoRenewPeriod);
        expected.setCreatedTimestamp(topic.getCreatedTimestamp());
        expected.setDeleted(false);
        expected.setTimestampLower(updateTimestamp);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(SUCCESS, updateTimestamp, topic.getId());
        assertEntity(expected);
        assertEquals(1L, entityRepository.count());
    }

    @Test
    void updateTopicTestError() {
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

        var transaction = createUpdateTopicTransaction(
                topicId, 11L, 21, updatedAdminKey, updatedSubmitKey, "updated-memo", null, 30L);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void updateTopicTestTopicNotFound() {
        var adminKey = keyFromString("updated-admin-key");
        var submitKey = keyFromString("updated-submit-key");
        var consensusTimestamp = 6_000_000L;
        var responseCode = SUCCESS;
        var memo = "updated-memo";
        var autoRenewAccount = EntityId.of(0L, 0L, 1L, EntityType.ACCOUNT);
        // Topic does not get stored in the repository beforehand.

        var transaction = createUpdateTopicTransaction(
                TOPIC_ID, 11L, 0, adminKey, submitKey, memo, autoRenewAccount.getId(), 30L);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());

        var expectedTopic =
                createTopicEntity(TOPIC_ID, 11L, 0, adminKey, submitKey, memo, autoRenewAccount.getId(), 30L);
        expectedTopic.setDeleted(false);
        expectedTopic.setTimestampLower(consensusTimestamp);
        assertThat(entity).isEqualTo(expectedTopic);
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.1500, 10, 20, admin-key, submit-key, memo, 5000000, 0, 0, , , , 1, 30, , 0",
        "0.0.1500, 10, 20, admin-key, submit-key, memo, 5000000, , , , , , 1, 30, , ",
        "0.0.1500, 10, 20, admin-key, submit-key, memo, 5000000, , , , , '', 1, 30, , ",
        "0.0.1501, 0, 0, '', '', '', 5000001, 0, 0, , , , , , ,",
        "0.0.1502, , , admin-key, submit-key, memo, 5000002, 10, 20, updated-admin-key, updated-submit-key, "
                + "updated-memo, 1, 30, 11, 31",
        "0.0.1503, , , , , '', 5000003, 11, 21, admin-key, submit-key, memo, , , 1, 30"
    })
    void updateTopicTestPartialUpdates(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key adminKey,
            @ConvertWith(KeyConverter.class) Key submitKey,
            String memo,
            long consensusTimestamp,
            Long updatedExpirationTimeSeconds,
            Integer updatedExpirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key updatedAdminKey,
            @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
            String updatedMemo,
            Long autoRenewAccountNum,
            Long autoRenewPeriod,
            Long updatedAutoRenewAccountNum,
            Long updatedAutoRenewPeriod) {
        // Store topic to be updated.
        var topic = createTopicEntity(
                topicId,
                expirationTimeSeconds,
                expirationTimeNanos,
                adminKey,
                submitKey,
                memo,
                autoRenewAccountNum,
                autoRenewPeriod);
        entityRepository.save(topic);

        if (updatedAutoRenewAccountNum != null) {
            topic.setAutoRenewAccountId(updatedAutoRenewAccountNum);
        }
        if (updatedAutoRenewPeriod != null) {
            topic.setAutoRenewPeriod(updatedAutoRenewPeriod);
        }
        if (updatedExpirationTimeSeconds != null && updatedExpirationTimeNanos != null) {
            topic.setExpirationTimestamp(
                    DomainUtils.convertToNanosMax(updatedExpirationTimeSeconds, updatedExpirationTimeNanos));
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

        var responseCode = SUCCESS;
        var transaction = createUpdateTopicTransaction(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedAdminKey,
                updatedSubmitKey,
                updatedMemo,
                updatedAutoRenewAccountNum,
                updatedAutoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        if (updatedAutoRenewAccountNum != null) {
            topic.setAutoRenewAccountId(updatedAutoRenewAccountNum);
        }
        topic.setDeleted(false);
        topic.setTimestampLower(consensusTimestamp);

        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTest() {
        var consensusTimestamp = 7_000_000L;
        var responseCode = SUCCESS;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        entityRepository.save(topic);

        // Setup expected data
        topic.setDeleted(true);
        topic.setTimestampLower(consensusTimestamp);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(SUCCESS, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestTopicNotFound() {
        var consensusTimestamp = 10_000_000L;
        var responseCode = SUCCESS;

        // Setup expected data
        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        topic.setDeleted(true);
        topic.setTimestampLower(consensusTimestamp);
        // Topic not saved to the repository.

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestError() {
        var consensusTimestamp = 9_000_000L;
        var responseCode = INSUFFICIENT_ACCOUNT_BALANCE;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, 10L, 20, null, null, "", null, null);
        entityRepository.save(topic);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.9000, test-message0, 9000000, runninghash, 1, 1, , , , false, 0",
        "0.0.9001, test-message1, 9000001, runninghash1, 9223372036854775807, 2, 1, 1, 89999999, false, 0",
        "0.0.9001, test-message2, 9000001, runninghash2, 9223372036854775807, 2, 2, 4, 89999999, false, 0",
        "0.0.9001, test-message3, 9000001, runninghash3, 9223372036854775807, 2, 4, 4, 89999999, false, 0",
        "0.0.9001, test-message4, 9000001, runninghash3, 9223372036854775807, 2, 4, 4, 89999999, true, 7",
    })
    void submitMessageTest(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            String message,
            long consensusTimestamp,
            String runningHash,
            long sequenceNumber,
            int runningHashVersion,
            Integer chunkNum,
            Integer chunkTotal,
            Long validStartNs,
            Boolean scheduled,
            Integer nonce) {
        var responseCode = SUCCESS;

        TransactionID initialTransactionId = null;
        if (chunkNum != null) {
            initialTransactionId = createTransactionID(
                    PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);
        }
        var topicMessage = createTopicMessage(
                topicId,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                topicId, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, topicId.getTopicNum());
        assertEquals(0L, entityRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestTopicNotFound() {
        var responseCode = SUCCESS;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var topicMessage = createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        assertEquals(0L, entityRepository.count());
        assertEquals(1L, topicMessageRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    @Test
    void submitMessageDisabled() {
        // given
        entityProperties.getPersist().setTopics(false);
        var responseCode = SUCCESS;
        var topicId = TOPIC_ID;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 1;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                topicId, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        entityProperties.getPersist().setTopics(true);
    }

    @Test
    void submitMessageTestFiltered() {
        // given
        var responseCode = SUCCESS;
        var topicId = (TopicID)
                new TopicIdArgumentConverter().convert("0.0.999", null); // excluded in application-default.yml
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 1;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                topicId, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        // if the transaction is filtered out, nothing in it should affect the state
        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageTestInvalidChunkInfo() {
        // given
        var id = 10_000_000L;
        var topicId = TopicID.newBuilder().setTopicNum(9000).build();
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId =
                createTransactionID(null, TestUtils.toTimestamp(Long.MAX_VALUE, Integer.MAX_VALUE), scheduled, nonce);
        var transaction = createSubmitMessageTransaction(topicId, "message", 3, 5, initialTransactionId);
        var transactionRecord = createTransactionRecord(topicId, 10_000L, "running-hash".getBytes(), 2, id, SUCCESS);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        assertEquals(0L, entityRepository.count());
        assertEquals(1L, topicMessageRepository.count());
        assertThat(topicMessageRepository.findById(id))
                .get()
                .extracting(TopicMessage::getValidStartTimestamp, TopicMessage::getPayerAccountId)
                .containsExactly(null, PAYER_ACCOUNT_ID);
    }

    @Test
    void submitMessageTestTopicError() {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var consensusTimestamp = 11_000_000L;
        var message = "message";
        var sequenceNumber = 11_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        assertEquals(0, entityRepository.count());
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
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        var topic = createTopicEntity(TOPIC_ID, null, null, null, null, "", null, null);
        // Topic NOT saved in the repository.

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.getEntityNum(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        assertEquals(0, entityRepository.count());
        assertEquals(0, topicMessageRepository.count());
    }

    private com.hederahashgraph.api.proto.java.Transaction createCreateTopicTransaction(
            Key adminKey, Key submitKey, String memo, Long autoRenewAccount, Long autoRenewPeriod) {
        var innerBody = ConsensusCreateTopicTransactionBody.newBuilder();
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(
                    AccountID.newBuilder().setAccountNum(autoRenewAccount).build());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(autoRenewPeriod).build());
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
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    @Test
    void submitMessageTestNoInitialTransactionId() {
        var responseCode = SUCCESS;
        var message = "test-message1-no-initial-transaction";
        var consensusTimestamp = 10_000_000L;
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;

        var topicMessage = createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                null);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, null);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, TOPIC_ID.getTopicNum());
        assertEquals(0L, entityRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    private TransactionRecord createTransactionRecord(
            TopicID topicId, long consensusTimestamp, ResponseCodeEnum responseCode) {
        return createTransactionRecord(
                topicId, 10_000L, "running_hash".getBytes(), 1, consensusTimestamp, responseCode);
    }

    private TransactionRecord createTransactionRecord(
            TopicID topicId,
            Long topicSequenceNumber,
            byte[] topicRunningHash,
            int runningHashVersion,
            long consensusTimestamp,
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
            receipt.setTopicRunningHashVersion(runningHashVersion);
        }
        var consensusTimestampProto = TestUtils.toTimestamp(consensusTimestamp);
        var transactionRecord = TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .setConsensusTimestamp(consensusTimestampProto)
                .setTransactionHash(ByteString.copyFrom(DigestUtils.sha384(consensusTimestampProto.toByteArray())));
        return transactionRecord.build();
    }

    private TransactionBody.Builder createTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionValidDuration(
                        Duration.newBuilder().setSeconds(10).build())
                .setNodeAccountID(TestUtils.toAccountId(NODE_ID))
                .setTransactionID(TestUtils.toTransactionId(TRANSACTION_ID))
                .setMemo(TRANSACTION_MEMO);
    }

    private Entity createTopicEntity(
            TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            Key adminKey,
            Key submitKey,
            String memo,
            Long autoRenewAccountNum,
            Long autoRenewPeriod) {
        Entity topic = EntityId.of(topicId).toEntity();

        if (autoRenewAccountNum != null) {
            topic.setAutoRenewAccountId(autoRenewAccountNum);
        }
        if (autoRenewPeriod != null) {
            topic.setAutoRenewPeriod(autoRenewPeriod);
        }
        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            topic.setExpirationTimestamp(DomainUtils.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
        }
        if (null != adminKey) {
            topic.setKey(adminKey.toByteArray());
        }
        if (null != submitKey) {
            topic.setSubmitKey(submitKey.toByteArray());
        }

        topic.setDeclineReward(false);
        topic.setEthereumNonce(0L);
        topic.setMemo(memo);
        topic.setType(EntityType.TOPIC);
        topic.setStakedNodeId(-1L);
        topic.setStakePeriodStart(-1L);
        return topic;
    }

    private TopicMessage createTopicMessage(
            TopicID topicId,
            String message,
            long sequenceNumber,
            String runningHash,
            long consensusTimestamp,
            int runningHashVersion,
            Integer chunkNum,
            Integer chunkTotal,
            EntityId payerAccountId,
            TransactionID initialTransactionID) {

        var topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp);
        if (initialTransactionID != null) {
            topicMessage.setInitialTransactionId(initialTransactionID.toByteArray());
        }
        topicMessage.setTopicId(EntityId.of("0.0." + topicId.getTopicNum(), EntityType.TOPIC));
        topicMessage.setMessage(message.getBytes());
        topicMessage.setPayerAccountId(payerAccountId);
        topicMessage.setSequenceNumber(sequenceNumber);
        topicMessage.setRunningHash(runningHash.getBytes());
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setChunkNum(chunkNum);
        topicMessage.setChunkTotal(chunkTotal);

        return topicMessage;
    }

    private com.hederahashgraph.api.proto.java.Transaction createUpdateTopicTransaction(
            TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            Key adminKey,
            Key submitKey,
            String memo,
            Long autoRenewAccount,
            Long autoRenewPeriod) {
        var innerBody = ConsensusUpdateTopicTransactionBody.newBuilder().setTopicID(topicId);

        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            innerBody.setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos));
        }
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(AccountID.newBuilder()
                    .setShardNum(topicId.getShardNum())
                    .setRealmNum(topicId.getRealmNum())
                    .setAccountNum(autoRenewAccount)
                    .build());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(autoRenewPeriod).build());
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
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createDeleteTopicTransaction(TopicID topicId) {
        var innerBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId);
        var body = createTransactionBody().setConsensusDeleteTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createSubmitMessageTransaction(
            TopicID topicId, String message, Integer chunkNum, Integer chunkTotal, TransactionID initialTransactionID) {
        var submitMessageTransactionBodyBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(topicId)
                .setMessage(ByteString.copyFrom(message.getBytes()));

        if (chunkNum != null) {
            ConsensusMessageChunkInfo.Builder chunkInfoBuilder =
                    ConsensusMessageChunkInfo.newBuilder().setNumber(chunkNum).setTotal(chunkTotal);
            if (initialTransactionID != null) {
                chunkInfoBuilder.setInitialTransactionID(initialTransactionID);
            }
            submitMessageTransactionBodyBuilder.setChunkInfo(chunkInfoBuilder);
        }

        var innerBody = submitMessageTransactionBodyBuilder.build();

        var body = createTransactionBody().setConsensusSubmitMessage(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private TransactionID createTransactionID(
            Long payerAccountIdNum, Timestamp validStartNs, Boolean scheduled, Integer nonce) {
        TransactionID.Builder transactionIdBuilder = TransactionID.newBuilder();
        if (payerAccountIdNum != null) {
            transactionIdBuilder.setAccountID(
                    AccountID.newBuilder().setAccountNum(payerAccountIdNum).build());
        }
        if (validStartNs != null) {
            transactionIdBuilder.setTransactionValidStart(validStartNs);
        }
        if (scheduled != null) {
            transactionIdBuilder.setScheduled(scheduled);
        }
        if (nonce != null) {
            transactionIdBuilder.setNonce(nonce);
        }
        return transactionIdBuilder.build();
    }

    private Entity getTopicEntity(TopicID topicId) {
        return getEntity(EntityId.of(topicId));
    }
}
