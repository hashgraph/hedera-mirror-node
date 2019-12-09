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

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.util.Utility;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.domain.Entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.jdbc.Sql;

import javax.annotation.Resource;
import javax.sql.DataSource;

// Class manually commits so have to manually cleanup tables
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class EntitiesTest extends IntegrationTest {

    @Resource
    private DataSource dataSource;

    @Resource
    private EntityRepository repo;

    static final int TOPIC_ENTITY_TYPE_ID = 4;

    @ParameterizedTest
    @CsvSource({
            "0.0.65537, 10, 20, admin-key, submit-key, 30, empty",
            "0.0.2147483647, 9223372036854775807, 9223372036854775807, null, null, 9223372036854775807, memo",
            "0.0.1, -9223372036854775808, -9223372036854775808, empty, empty, -9223372036854775808, memo"
    })
    void createTopicTest(String topicId, long expirationTimeSeconds, long expirationTimeNanos, String adminKey,
                         String submitKey, long validStartTime, String memo) throws Exception {
        long entityId;
        var ak = TestUtils.toByteArray(adminKey);
        var sk = TestUtils.toByteArray(submitKey);
        memo = TestUtils.toStringWithNullOrEmpty(memo);

        var tid = TestUtils.toTopicId(topicId);

        try (var conn = dataSource.getConnection()) {
            var cut = new com.hedera.mirror.importer.parser.record.Entities(conn);
            entityId = cut.createEntity(tid, expirationTimeSeconds, expirationTimeNanos, ak, sk, validStartTime, memo);
        }

        assertThat(repo.findById(entityId).get())
                .returns(tid.getTopicNum(), from(Entities::getEntityNum))
                .returns(tid.getRealmNum(), from(Entities::getEntityRealm))
                .returns(Utility
                                .convertToNanosMax(expirationTimeSeconds, expirationTimeNanos),
                        from(Entities::getExpiryTimeNs))
                .returns(expirationTimeSeconds, from(Entities::getExpiryTimeSeconds))
                .returns(expirationTimeNanos, from(Entities::getExpiryTimeNanos))
                .returns(ak, from(Entities::getKey))
                .returns(sk, from(Entities::getSubmitKey))
                .returns(validStartTime, from(Entities::getTopicValidStartTime))
                .returns(false, from(Entities::isDeleted))
                .returns(memo, from(Entities::getMemo))
                .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
    }

    @Test
    void createTopicDbDefaultsTest() throws Exception {
        long entityId;
        try (var conn = dataSource.getConnection()) {
            var cut = new com.hedera.mirror.importer.parser.record.Entities(conn);
            entityId = cut.createEntity(TestUtils.toTopicId("0.0.1"), 0, 0, null, null, 0, null);
        }
        assertThat(repo.findById(entityId).get())
                .returns(1L, from(Entities::getEntityNum))
                .returns(0L, from(Entities::getEntityRealm))
                .returns(null, from(Entities::getExpiryTimeNs))
                .returns(null, from(Entities::getExpiryTimeSeconds))
                .returns(null, from(Entities::getExpiryTimeNanos))
                .returns(null, from(Entities::getKey))
                .returns(null, from(Entities::getSubmitKey))
                .returns(null, from(Entities::getTopicValidStartTime))
                .returns(null, from(Entities::getMemo))
                .returns(false, from(Entities::isDeleted))
                .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
    }

    // Using 0.0.-1 to indicate NOT to create the topic, just run the update.
    @ParameterizedTest
    @CsvSource({
            "0.0.65537, 10, 20, admin-key, submit-key, 30, memo, 11, 21, updated-admin-key, updated-submit-key, 31, me",
            "0.0.1234, 10, 20, admin-key, submit-key, 30, me, 0, 0, null, null, 0, memo",
            "0.0.1235, 10, 20, null, null, 30, null, 0, 0, empty, empty, 0, null",
            "0.0.1236, 10, 20, null, empty, 30, memo, 0, 21, empty, null, 0, null",
            "0.0.1237, 10, 20, empty, null, 30, memo, 11, 0, empty, null, 0, empty",
            "0.0.-1, 0, 0, null, null, 30, null, 11, 21, updated-admin-key, updated-submit-key, 31, null"
    })
    void updateTopicTest(String topicId, long expirationTimeSeconds, long expirationTimeNanos, String adminKey,
                         String submitKey, long validStartTime, String memo, long updatedExpirationTimeSeconds,
                         long updatedExpirationTimeNanos,
                         String updatedAdminKey, String updatedSubmitKey, long updatedValidStartTime,
                         String updatedMemo) throws Exception {
        long entityId;
        var ak = TestUtils.toByteArray(adminKey);
        var sk = TestUtils.toByteArray(submitKey);
        var updatedAk = TestUtils.toByteArray(updatedAdminKey);
        var updatedSk = TestUtils.toByteArray(updatedSubmitKey);
        var tid = TestUtils.toTopicId(topicId);
        memo = TestUtils.toStringWithNullOrEmpty(memo);
        updatedMemo = TestUtils.toStringWithNullOrEmpty(updatedMemo);

        try (var conn = dataSource.getConnection()) {
            var cut = new com.hedera.mirror.importer.parser.record.Entities(conn);
            if (-1 != tid.getTopicNum()) {
                cut.createEntity(tid, expirationTimeSeconds, expirationTimeNanos, ak, sk, validStartTime, memo);
            }
            entityId = cut.updateEntity(tid, updatedExpirationTimeSeconds, updatedExpirationTimeNanos, updatedAk,
                    updatedSk, updatedValidStartTime, updatedMemo);
        }

        // When 0s or nulls are passed, those fields are expected to remain unmodified.
        var expectedExpirationTimeSeconds = updatedExpirationTimeSeconds;
        var expectedExpirationTimeNanos = updatedExpirationTimeNanos;
        var expectedAdminKey = (null == updatedAk) ? ak : updatedAk;
        var expectedSubmitKey = (null == updatedSk) ? sk : updatedSk;
        var expectedValidStartTime = (0 == updatedValidStartTime) ? validStartTime : updatedValidStartTime;
        if ((0 == updatedExpirationTimeSeconds) && (0 == updatedExpirationTimeNanos)) {
            expectedExpirationTimeSeconds = expirationTimeSeconds;
            expectedExpirationTimeNanos = expirationTimeNanos;
        }
        var expectedMemo = (null == updatedMemo) ? memo : updatedMemo;

        assertThat(repo.findById(entityId).get())
                .returns(tid.getTopicNum(), from(Entities::getEntityNum))
                .returns(tid.getRealmNum(), from(Entities::getEntityRealm))
                .returns(Utility
                                .convertToNanosMax(expectedExpirationTimeSeconds, expectedExpirationTimeNanos),
                        from(Entities::getExpiryTimeNs))
                .returns(expectedExpirationTimeSeconds, from(Entities::getExpiryTimeSeconds))
                .returns(expectedExpirationTimeNanos, from(Entities::getExpiryTimeNanos))
                .returns(expectedAdminKey, from(Entities::getKey))
                .returns(expectedSubmitKey, from(Entities::getSubmitKey))
                .returns(expectedValidStartTime, from(Entities::getTopicValidStartTime))
                .returns(false, from(Entities::isDeleted))
                .returns(expectedMemo, from(Entities::getMemo))
                .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
    }

    @Test
    void deleteTopicTest() throws Exception {
        long entityId;
        var topicId = TestUtils.toTopicId("0.0.2");

        try (var conn = dataSource.getConnection()) {
            var cut = new com.hedera.mirror.importer.parser.record.Entities(conn);
            entityId = cut.createEntity(topicId, 0, 0, null, null, 0, null);
            cut.deleteEntity(topicId);
        }

        assertThat(repo.findById(entityId).get())
                .returns(2L, from(Entities::getEntityNum))
                .returns(0L, from(Entities::getEntityRealm))
                .returns(null, from(Entities::getExpiryTimeNs))
                .returns(null, from(Entities::getExpiryTimeSeconds))
                .returns(null, from(Entities::getExpiryTimeNanos))
                .returns(null, from(Entities::getKey))
                .returns(null, from(Entities::getSubmitKey))
                .returns(null, from(Entities::getTopicValidStartTime))
                .returns(true, from(Entities::isDeleted))
                .returns(TOPIC_ENTITY_TYPE_ID, from(Entities::getEntityTypeId));
    }
}
