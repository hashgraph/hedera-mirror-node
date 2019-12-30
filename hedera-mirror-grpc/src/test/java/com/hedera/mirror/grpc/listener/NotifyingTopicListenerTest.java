package com.hedera.mirror.grpc.listener;

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

import java.time.Duration;
import java.time.Instant;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class NotifyingTopicListenerTest extends AbstractTopicListenerTest {

    @Resource
    private NotifyingTopicListener topicListener;

    @Override
    protected TopicListener getTopicListener() {
        return topicListener;
    }

    @Test
    void jsonError() {
        Mono<?> generator = databaseClient.execute("NOTIFY topic_message, 'invalid'").fetch().first();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.block())
                .expectError(RuntimeException.class)
                .verify(Duration.ofMillis(500));
    }
}
