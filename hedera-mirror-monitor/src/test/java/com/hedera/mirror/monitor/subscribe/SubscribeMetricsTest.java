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

import static com.hedera.mirror.monitor.subscribe.SubscribeMetrics.METRIC_DURATION;
import static com.hedera.mirror.monitor.subscribe.SubscribeMetrics.METRIC_E2E;
import static com.hedera.mirror.monitor.subscribe.SubscribeMetrics.TAG_PROTOCOL;
import static com.hedera.mirror.monitor.subscribe.SubscribeMetrics.TAG_SCENARIO;
import static com.hedera.mirror.monitor.subscribe.SubscribeMetrics.TAG_SUBSCRIBER;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.monitor.ScenarioStatus;
import com.hedera.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;

class SubscribeMetricsTest {

    private MeterRegistry meterRegistry;
    private SubscribeProperties subscribeProperties;
    private SubscribeMetrics subscribeMetrics;
    private AbstractSubscriberProperties properties;
    private StringWriter logOutput;
    private WriterAppender writerAppender;

    @BeforeEach
    void setup() {
        properties = new GrpcSubscriberProperties();
        properties.setName("Test");
        meterRegistry = new SimpleMeterRegistry();
        subscribeProperties = new SubscribeProperties();
        subscribeMetrics = new SubscribeMetrics(meterRegistry, subscribeProperties);

        logOutput = new StringWriter();
        writerAppender = WriterAppender.newBuilder()
                .setName("stringAppender")
                .setTarget(logOutput)
                .build();
        Logger logger = (Logger) LogManager.getLogger(subscribeMetrics);
        logger.addAppender(writerAppender);
        writerAppender.start();
    }

    @AfterEach
    void after() {
        writerAppender.stop();
        Logger logger = (Logger) LogManager.getLogger(subscribeMetrics);
        logger.removeAppender(writerAppender);
    }

    @Test
    void recordDuration() {
        TestScenario subscription = new TestScenario();
        SubscribeResponse response1 = response(subscription);
        SubscribeResponse response2 = response(subscription);
        subscribeMetrics.onNext(response1);
        subscribeMetrics.onNext(response2);

        assertThat(meterRegistry.find(METRIC_DURATION).timeGauges())
                .hasSize(1)
                .first()
                .returns((double) subscription.getElapsed().toNanos(), t -> t.value(TimeUnit.NANOSECONDS))
                .returns(subscription.getProtocol().toString(), t -> t.getId().getTag(TAG_PROTOCOL))
                .returns(subscription.getName(), t -> t.getId().getTag(TAG_SCENARIO))
                .returns(String.valueOf(subscription.getId()), t -> t.getId().getTag(TAG_SUBSCRIBER));
    }

    @Test
    void recordE2E() {
        TestScenario subscription = new TestScenario();
        SubscribeResponse response1 = response(subscription);
        subscribeMetrics.onNext(response1);

        assertThat(meterRegistry.find(METRIC_E2E).timers())
                .hasSize(1)
                .first()
                .returns(1L, t -> t.count())
                .returns(2.0, t -> t.mean(TimeUnit.SECONDS))
                .returns(2.0, t -> t.max(TimeUnit.SECONDS))
                .returns(subscription.getProtocol().toString(), t -> t.getId().getTag(TAG_PROTOCOL))
                .returns(subscription.getName(), t -> t.getId().getTag(TAG_SCENARIO))
                .returns(String.valueOf(subscription.getId()), t -> t.getId().getTag(TAG_SUBSCRIBER));

        subscription.setCount(subscription.getCount() + 1);
        SubscribeResponse response2 = response(subscription);
        subscribeMetrics.onNext(response2);

        assertThat(meterRegistry.find(METRIC_E2E).timers())
                .hasSize(1)
                .first()
                .returns(2L, t -> t.count())
                .returns(3.0, t -> t.mean(TimeUnit.SECONDS))
                .returns(4.0, t -> t.max(TimeUnit.SECONDS))
                .returns(subscription.getProtocol().toString(), t -> t.getId().getTag(TAG_PROTOCOL))
                .returns(subscription.getName(), t -> t.getId().getTag(TAG_SCENARIO))
                .returns(String.valueOf(subscription.getId()), t -> t.getId().getTag(TAG_SUBSCRIBER));
    }

    @Test
    void status() {
        TestScenario testSubscription1 = new TestScenario();
        TestScenario testSubscription2 = new TestScenario();
        testSubscription2.setName("Test2");

        subscribeMetrics.onNext(response(testSubscription1));
        subscribeMetrics.onNext(response(testSubscription2));
        subscribeMetrics.status();

        assertThat(logOutput).asString()
                .hasLineCount(2)
                .contains("GRPC scenario Test received 1 responses in 1s at 1.0/s. Errors: {}")
                .contains("GRPC scenario Test received 1 responses in 1s at 1.0/s. Errors: {}");
    }

    @Test
    void statusDisabled() {
        subscribeProperties.setEnabled(false);

        subscribeMetrics.onNext(response(new TestScenario()));
        subscribeMetrics.status();

        assertThat(logOutput).asString().isEmpty();
    }

    @Test
    void statusNotRunning() {
        TestScenario testSubscription = new TestScenario();
        testSubscription.setStatus(ScenarioStatus.COMPLETED);

        subscribeMetrics.onNext(response(testSubscription));
        subscribeMetrics.status();

        assertThat(logOutput).asString().isEmpty();
    }

    private SubscribeResponse response(Scenario scenario) {
        Instant timestamp = Instant.now().minusSeconds(5L);
        return SubscribeResponse.builder()
                .publishedTimestamp(timestamp)
                .consensusTimestamp(timestamp.plusSeconds(1L * scenario.getCount()))
                .receivedTimestamp(timestamp.plusSeconds(2L * scenario.getCount()))
                .scenario(scenario)
                .build();
    }
}
