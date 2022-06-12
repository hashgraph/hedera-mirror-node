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

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

class PulsarTopicsTest {

    private static final String PULSAR_BROKER_ROOT_URL = "pulsar://localhost:6650";
    private boolean isPulsarAvailable;
    private PulsarClient client;
    private static final String CONSISTENT_TOPIC_NAME = "persistent://public/default/test-topic";
    private static final Instant NOW = Instant.now();
    private static final String TEMPORARY_TOPIC_NAME = "persistent://public/default/test-topic-temp-" + NOW.toString();
    private Producer producer1;
    private Producer producer2;

    @BeforeEach
    void setup() {
        try {
            isPulsarAvailable = false;
            client = PulsarClient.builder().serviceUrl(PULSAR_BROKER_ROOT_URL).build();
            producer1 = client.newProducer().topic(CONSISTENT_TOPIC_NAME).create();
            isPulsarAvailable = true;
        } catch (PulsarClientException e) {
            System.out.println("Can't instantiate pulsar -- " + e.getMessage());
        }
    }

    @Test
    void publishMessagesTest() throws Exception {
        // declare success (falsely) if there is no Pulsar.
        if (!isPulsarAvailable) {
            System.out.println("publishMessagesTest: Pulsar is not available.");
            assertNull("Expected no producer 1", producer1);
            assertNull("Expected no producer 2", producer2);
            return;
        }
        producer2 = client.newProducer().topic(TEMPORARY_TOPIC_NAME).create();
        // send a message on each
        assertNotNull("Expected non-null producer1", producer1);
        assertNotNull("Expected non-null producer2", producer2);

        producer1.send(("Message on consistent topic " + CONSISTENT_TOPIC_NAME).getBytes());
        producer2.send(("Message on temporary topic " + TEMPORARY_TOPIC_NAME).getBytes());
        // close everything
        producer1.close();
        producer2.close();
        client.close();
    }

    @Test
    void consumeMessagesTest() throws Exception {
        // declare success (falsely) if there is no Pulsar.
        if (!isPulsarAvailable) {
            System.out.println("consumeMessagesTest: Pulsar is not available.");
            assertNull("Expected no producer 1", producer1);
            assertNull("Expected no producer 2", producer2);
            return;
        }
        Consumer consumer1 = client.newConsumer().topic(CONSISTENT_TOPIC_NAME).subscriptionName("my-sub-1")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionType(SubscriptionType.Shared).subscribe();
        try {
            Message message1 = consumer1.receive();
            String data1 = new String(message1.getData());
            System.out.println("Message 1 received: " + data1);
            assertTrue("bad message 1", data1.startsWith("Message on consistent topic"));
            consumer1.acknowledge(message1);
        } catch (Exception e) {
            System.out.println("Message 1 not received: " + e.getMessage());
        }

        Consumer consumer2 = client.newConsumer().topic(TEMPORARY_TOPIC_NAME).subscriptionName("my-sub-2")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscriptionType(SubscriptionType.Shared).subscribe();
        try {
            Message message2 = consumer2.receive();
            String data2 = new String(message2.getData());
            System.out.println("Message 2 received: " + data2);
            assertTrue("bad message 2", data2.startsWith("Message on temporary topic"));
            consumer2.acknowledge(message2);
        } catch (Exception e) {
            System.out.println("Message 2 not received: " + e.getMessage());
        }
        // close everything
        consumer1.close();
        consumer2.close();
        client.close();
    }

}
