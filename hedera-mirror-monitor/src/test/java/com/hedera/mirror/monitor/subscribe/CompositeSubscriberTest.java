package com.hedera.mirror.monitor.subscribe;

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

import static com.hedera.mirror.monitor.subscribe.AbstractSubscriber.METRIC_DURATION;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.MirrorNodeProperties;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriber;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriberProperties;

class CompositeSubscriberTest {

    private MonitorProperties monitorProperties;
    private SubscribeProperties subscribeProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private MeterRegistry meterRegistry;
    private WebClient.Builder webClient;
    private CompositeSubscriber compositeSubscriber;

    @BeforeEach
    void setup() {
        MirrorNodeProperties mirrorNodeProperties = new MirrorNodeProperties();
        mirrorNodeProperties.getGrpc().setHost("127.0.0.1");
        mirrorNodeProperties.getRest().setHost("127.0.0.1");
        monitorProperties = new MonitorProperties();
        monitorProperties.setMirrorNode(mirrorNodeProperties);
        subscribeProperties = new SubscribeProperties();
        meterRegistry = new SimpleMeterRegistry();
        webClient = WebClient.builder();
        compositeSubscriber = new CompositeSubscriber(monitorProperties, subscribeProperties, meterRegistry, webClient);

        restSubscriberProperties = new RestSubscriberProperties();
        restSubscriberProperties.setName("rest");
        subscribeProperties.getRest().add(restSubscriberProperties);
    }

    @Test
    void close() {
        compositeSubscriber.close();
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).isEmpty();
    }

    @Test
    void onPublish() {
        PublishResponse response = PublishResponse.builder()
                .request(PublishRequest.builder()
                        .timestamp(Instant.now())
                        .type(TransactionType.CONSENSUS_SUBMIT_MESSAGE)
                        .build())
                .timestamp(Instant.now())
                .transactionId(TransactionId.withValidStart(AccountId.fromString("0.0.1000"), Instant.ofEpochSecond(1)))
                .build();
        compositeSubscriber.onPublish(response);
        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges()).hasSize(1);
    }

    @Test
    void allEnabled() {
        assertThat(compositeSubscriber.subscribers.get())
                .hasSize(1)
                .hasAtLeastOneElementOfType(RestSubscriber.class);
    }

    @Test
    void allDisabled() {
        restSubscriberProperties.setEnabled(false);
        assertThat(compositeSubscriber.subscribers.get()).isEmpty();
    }

    @Test
    void subscribeDisabled() {
        subscribeProperties.setEnabled(false);
        assertThat(compositeSubscriber.subscribers.get()).isEmpty();
    }
}
