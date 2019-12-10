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

import com.hedera.mirror.importer.TopicIdConverter;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.domain.Entities;

import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.Test;
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

    // This is a test on Entities.createEntity where adminKey, submitKey, and memo are null, which isn't a case hit
    // by the RecordFileLogger test.
    @Test
    void createTopicDbDefaultsTest() throws Exception {
        long entityId;
        var topicId = (TopicID) new TopicIdConverter().convert("0.0.20000", null);
        try (var conn = dataSource.getConnection()) {
            var cut = new com.hedera.mirror.importer.parser.record.Entities(conn);
            entityId = cut.createEntity(topicId, 0, 0, null, null, 0, null);
        }
        assertThat(repo.findById(entityId).get())
                .returns(topicId.getTopicNum(), from(Entities::getEntityNum))
                .returns(topicId.getRealmNum(), from(Entities::getEntityRealm))
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
}
