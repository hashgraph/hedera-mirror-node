package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.api.gax.rpc.NotFoundException;
import com.google.pubsub.v1.PubsubMessage;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gcp.pubsub.PubSubAdmin;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.hedera.mirror.importer.parser.record.pubsub.PubSubProperties;

@ActiveProfiles("pubsub")
@SpringBootTest(properties = {
        "spring.cloud.gcp.pubsub.enabled=true",
        "hedera.mirror.importer.parser.record.entity.enabled=false",
        "hedera.mirror.importer.parser.record.pubsub.enabled=true"})
public class PubSubIntegrationTest extends IntegrationTest {
    private static final String SUBSCRIPTION = "testSubscription";

    @Resource
    private PubSubProperties properties;
    @Resource
    private PubSubTemplate pubSubTemplate;
    @Resource
    private PubSubAdmin pubSubAdmin;

    @BeforeEach
    void setup() {
        String topicName = properties.getTopicName();
        // delete old topic and subscription if present
        try {
            pubSubAdmin.deleteTopic(topicName);
            pubSubAdmin.deleteSubscription(SUBSCRIPTION);
        } catch (NotFoundException e) {
            // ignored
        }
        pubSubAdmin.createTopic(topicName);
        pubSubAdmin.createSubscription(SUBSCRIPTION, topicName);
    }

    // Synchronously waits for numMessages from the subscription. Acks them and extracts payloads from them.
    public List<PubsubMessage> getAllMessages(int numMessages) {
        return pubSubTemplate.pull(SUBSCRIPTION, numMessages, false)
                .stream()
                .map(m -> {
                    m.ack();
                    return m.getPubsubMessage();
                })
                .collect(Collectors.toList());
    }
}
