package com.hedera.mirror.importer.pulsar;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

// initial version of this unit test just:
// (1) publishes a new message on a topic that presumably already existed
// (2) creates a new topic and post a message to it
// (3) read back/verify the new messages
// (4) deletes the new topic

// in the future, an additional test will be added to verify the future behavior of the record importer
// publishing to its own persistent topic.

import lombok.extern.log4j.Log4j2;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Log4j2
class PulsarTopicsTest {

    private static final String PULSAR_BROKER_ROOT_URL = "pulsar://localhost:6650";
    private static final String CONSISTENT_TOPIC_NAME = "persistent://public/default/test-topic";
    private static final Instant NOW = Instant.now();
    private static final String TEMPORARY_TOPIC_NAME = "persistent://public/default/test-topic-temp-" + NOW.toString();
    private static final String MESSAGE_1_PREFIX = "Message on consistent topic ";
    private static final String MESSAGE_2_PREFIX = "Message on temporary topic ";

    private PulsarClient client;

    @BeforeEach
    void setup() throws PulsarClientException {
        client = PulsarClient.builder().serviceUrl(PULSAR_BROKER_ROOT_URL).build();
    }

    @AfterEach
    void tearDown() throws PulsarClientException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void publishAndConsumeMessagesTest() throws Exception {
        // For now, this will throw an Exception if there is no Pulsar.
        // Replace with invocation of pulsar on testcontainer.
	try (
            Producer producer1 = client.newProducer().topic(CONSISTENT_TOPIC_NAME).create();
            Producer producer2 = client.newProducer().topic(TEMPORARY_TOPIC_NAME).create();
            Consumer consumer1 = client.newConsumer().topic(CONSISTENT_TOPIC_NAME).subscriptionName("my-sub-1")
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                    .subscriptionType(SubscriptionType.Shared).subscribe();
            Consumer consumer2 = client.newConsumer().topic(TEMPORARY_TOPIC_NAME).subscriptionName("my-sub-2")
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                    .subscriptionType(SubscriptionType.Shared).subscribe();
        ) {
            // send a message on each
            producer1.send((MESSAGE_1_PREFIX + CONSISTENT_TOPIC_NAME).getBytes());
            producer2.send((MESSAGE_2_PREFIX + TEMPORARY_TOPIC_NAME).getBytes());

            // receive message on first topic, make sure it is as expected.
            Message message1 = consumer1.receive();
            String data1 = new String(message1.getData());
            log.info("Message 1 received: {}", data1);
            assertTrue("bad message 1", data1.startsWith(MESSAGE_1_PREFIX));
            consumer1.acknowledge(message1);

            // receive message on second topic, make sure it is as expected.
            Message message2 = consumer2.receive();
            String data2 = new String(message2.getData());
            log.info("Message 2 received: {}", data2);
            assertTrue("bad message 2", data2.startsWith(MESSAGE_2_PREFIX));
            consumer2.acknowledge(message2);
        }
    }

}
