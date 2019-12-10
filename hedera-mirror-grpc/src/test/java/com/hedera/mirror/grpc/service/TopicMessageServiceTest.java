package com.hedera.mirror.grpc.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.annotation.Resource;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class TopicMessageServiceTest extends GrpcIntegrationTest {

    @Resource
    private TopicMessageService topicMessageService;

    @Resource
    private DomainBuilder domainBuilder;

    @Test
    void invalidFilter() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .realmNum(-1L)
                .topicNum(-1L)
                .startTime(null)
                .limit(-1)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .hasMessageContaining("realmNum: must be greater than or equal to 0")
                .hasMessageContaining("startTime: must not be null")
                .hasMessageContaining("topicNum: must be greater than or equal to 0");
    }

    @Test
    void endTimeBeforeStartTime() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.now())
                .endTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void endTimeEqualsStartTime() {
        Instant now = Instant.now();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(now)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void noMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    void historicalMessages() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(Instant.now())
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .verifyComplete();
    }
}
