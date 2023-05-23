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

package com.hedera.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class TopicMessageRepositoryTest extends GrpcIntegrationTest {

    @Resource
    private TopicMessageRepository topicMessageRepository;

    @Autowired
    private DomainBuilder domainBuilder;

    @Test
    void findByFilterEmpty() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(EntityId.of(100L, EntityType.TOPIC))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).isEmpty();
    }

    @Test
    void findByFilterNoMatch() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.now().plusSeconds(10))
                .topicId(EntityId.of(topicMessage1.getTopicId(), EntityType.TOPIC))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).isEmpty();
    }

    @Test
    void findByFilterWithTopicId() {
        TopicMessage topicMessage1 =
                domainBuilder.topicMessage(t -> t.topicId(1)).block();
        TopicMessage topicMessage2 =
                domainBuilder.topicMessage(t -> t.topicId(2)).block();
        TopicMessage topicMessage3 =
                domainBuilder.topicMessage(t -> t.topicId(3)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicId(EntityId.of(2L, EntityType.TOPIC))
                .startTime(topicMessage1.getConsensusTimestampInstant())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage2);
    }

    @Test
    void findByFilterWithStartTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage2.getConsensusTimestampInstant())
                .topicId(EntityId.of(topicMessage1.getTopicId(), EntityType.TOPIC))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage2, topicMessage3);
    }

    @Test
    void findByFilterWithEndTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage1.getConsensusTimestampInstant())
                .endTime(topicMessage3.getConsensusTimestampInstant())
                .topicId(EntityId.of(topicMessage1.getTopicId(), EntityType.TOPIC))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage1, topicMessage2);
    }

    @Test
    void findByFilterWithLimit() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(1)
                .startTime(topicMessage1.getConsensusTimestampInstant())
                .topicId(EntityId.of(topicMessage1.getTopicId(), EntityType.TOPIC))
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage1);
    }

    @Test
    void findLatest() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage4 = domainBuilder.topicMessage().block();
        Pageable pageable = PageRequest.of(0, 2);

        assertThat(topicMessageRepository.findLatest(topicMessage1.getConsensusTimestamp(), pageable))
                .containsExactly(topicMessage2, topicMessage3);
    }
}
