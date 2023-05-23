/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.topic;

import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_TABLE_TIME_PARTITION;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.TimePartition;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.TopicMessageLookupRepository;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class AbstractTopicMessageLookupIntegrationTest extends IntegrationTest {

    protected static final Duration RECORD_FILE_INTERVAL = StreamType.RECORD.getFileCloseInterval();

    private static final String CHECK_TABLE_EXISTENCE = "select 'topic_message_plain'::regclass";
    private static final String CREATE_DDL =
            """
            alter table topic_message rename to topic_message_plain;
            create table topic_message (like topic_message_plain including constraints, primary key (consensus_timestamp, topic_id)) partition by range (consensus_timestamp);
            create table topic_message_default partition of topic_message default;
            create table topic_message_00 partition of topic_message for values from ('1680000000000000000') to ('1682000000000000000');
            create table topic_message_01 partition of topic_message for values from ('1682000000000000000') to ('1684000000000000000');
            """;
    private static final String REVERT_DDL =
            """
            drop table topic_message cascade;
            alter table topic_message_plain rename to topic_message;
            """;

    @Autowired
    @Qualifier(CACHE_MANAGER_TABLE_TIME_PARTITION)
    private CacheManager cacheManager;

    @Resource
    protected EntityProperties entityProperties;

    @Autowired
    @Owner
    protected JdbcTemplate jdbcTemplate;

    @Resource
    protected TimePartitionService timePartitionService;

    @Resource
    protected TopicMessageLookupRepository topicMessageLookupRepository;

    protected List<TimePartition> partitions;

    @BeforeEach
    void setup() {
        entityProperties.getPersist().setTopics(true);
        entityProperties.getPersist().setTopicMessageLookups(true);
        createPartitions();
    }

    private void createPartitions() {
        if (isV1()) {
            jdbcTemplate.execute(CREATE_DDL);
        }

        partitions = timePartitionService.getTimePartitions("topic_message");
    }

    @AfterEach
    protected void revertPartitions() {
        if (!isV1()) {
            return;
        }

        try {
            jdbcTemplate.execute(CHECK_TABLE_EXISTENCE);
            jdbcTemplate.execute(REVERT_DDL);
            // clear cache so for v1 TimePartitionService won't return stale partition info
            cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        } catch (Exception e) {
            //
        }
    }
}
