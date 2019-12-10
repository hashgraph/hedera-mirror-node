package com.hedera.mirror.grpc.repository;

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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

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
    void findByFilterWithRealmNum() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage(t -> t.realmNum(0)).block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage(t -> t.realmNum(1)).block();
        domainBuilder.topicMessage(t -> t.realmNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .realmNum(1)
                .startTime(topicMessage1.getConsensusTimestamp())
                .build();

        topicMessageRepository.findByFilter(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage2)
                .verifyComplete();
    }

    @Test
    void findByFilterWithTopicNum() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage(t -> t.topicNum(1)).block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage(t -> t.topicNum(2)).block();
        domainBuilder.topicMessage(t -> t.topicNum(3)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(2)
                .startTime(topicMessage1.getConsensusTimestamp())
                .build();

        topicMessageRepository.findByFilter(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage2)
                .verifyComplete();
    }

    @Test
    void findByFilterWithStartTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage2.getConsensusTimestamp())
                .build();

        topicMessageRepository.findByFilter(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .verifyComplete();
    }

    @Test
    void findByFilterWithEndTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(topicMessage1.getConsensusTimestamp())
                .endTime(topicMessage3.getConsensusTimestamp())
                .build();

        topicMessageRepository.findByFilter(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .verifyComplete();
    }

    @Test
    void findByFilterWithLimit() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(1)
                .startTime(topicMessage1.getConsensusTimestamp())
                .build();

        topicMessageRepository.findByFilter(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .verifyComplete();
    }
}
