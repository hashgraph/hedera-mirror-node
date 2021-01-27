package com.hedera.mirror.grpc.repository;

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

import java.time.Instant;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class TopicMessageRepositoryTest extends GrpcIntegrationTest {

    @Resource
    private TopicMessageRepository topicMessageRepository;

    @Resource
    private DomainBuilder domainBuilder;

    @Test
    void findByFilterEmpty() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
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
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).isEmpty();
    }

    @Test
    void findByFilterWithRealmNum() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage(t -> t.realmNum(0)).block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage(t -> t.realmNum(1)).block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage(t -> t.realmNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .realmNum(1)
                .startTime(topicMessage1.getConsensusTimestampInstant())
                .build();

        assertThat(topicMessageRepository.findByFilter(filter)).containsExactly(topicMessage2);
    }

    @Test
    void findByFilterWithTopicNum() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage(t -> t.topicNum(1)).block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage(t -> t.topicNum(2)).block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage(t -> t.topicNum(3)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(2)
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

        assertThat(topicMessageRepository
                .findLatest(topicMessage1.getConsensusTimestamp(), pageable))
                .containsExactly(topicMessage2, topicMessage3);
    }
}
