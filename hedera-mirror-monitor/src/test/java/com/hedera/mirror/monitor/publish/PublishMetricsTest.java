package com.hedera.mirror.monitor.publish;

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

import io.grpc.Status;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.proto.TransactionReceipt;

class PublishMetricsTest {

    private static final String NODE_ACCOUNT_ID = "0.0.3";
    private static final String SCENARIO_NAME = "test";

    private MeterRegistry meterRegistry;
    private PublishMetrics publishMetrics;
    private PublishProperties publishProperties;
    private PublishScenario publishScenario;
    private StringWriter logOutput;
    private WriterAppender writerAppender;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        publishProperties = new PublishProperties();
        publishMetrics = new PublishMetrics(meterRegistry, publishProperties);

        PublishScenarioProperties publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName(SCENARIO_NAME);
        publishScenarioProperties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        publishScenario = new PublishScenario(publishScenarioProperties);

        logOutput = new StringWriter();
        writerAppender = WriterAppender.newBuilder()
                .setName("stringAppender")
                .setTarget(logOutput)
                .build();
        Logger logger = (Logger) LogManager.getLogger(publishMetrics);
        logger.addAppender(writerAppender);
        writerAppender.start();
    }

    @AfterEach
    void after() {
        writerAppender.stop();
        Logger logger = (Logger) LogManager.getLogger(publishMetrics);
        logger.removeAppender(writerAppender);
    }

    @Test
    void onSuccess() throws Exception {
        publishMetrics.onSuccess(response());
        publishMetrics.onSuccess(response());

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .extracting(t -> t.value())
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isPositive();

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_HANDLE).timers())
                .returns(PublishMetrics.SUCCESS, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(5.0);

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timers())
                .returns(PublishMetrics.SUCCESS, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void onErrorStatusRuntimeException() {
        Status status = Status.RESOURCE_EXHAUSTED;
        onError(status.asRuntimeException(), status.getCode().toString());
    }

    @Test
    void onErrorTimeoutException() {
        onError(new TimeoutException(), TimeoutException.class.getSimpleName());
    }

    void onError(Throwable throwable, String status) {
        publishMetrics.onError(new PublishException(request(), throwable));
        publishMetrics.onError(new PublishException(request(), throwable));

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .extracting(TimeGauge::value)
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isPositive();

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timers())
                .returns(status, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void statusError() {
        PublishException publishException = new PublishException(request(), new TimeoutException());
        publishScenario.onError(publishException);
        publishMetrics.onError(publishException);
        publishMetrics.status();
        assertThat(logOutput)
                .asString()
                .hasLineCount(1)
                .contains("Scenario " + SCENARIO_NAME + " published 0 transactions in")
                .contains("Errors: {TimeoutException=1}");
    }

    @Test
    void statusSuccess() throws Exception {
        PublishResponse response = response();
        publishScenario.onNext(response);
        publishMetrics.onSuccess(response);
        publishMetrics.status();
        assertThat(logOutput)
                .asString()
                .hasLineCount(1)
                .contains("Scenario " + SCENARIO_NAME + " published 1 transactions in")
                .contains("Errors: {}");
    }

    @Test
    void statusDisabled() throws Exception {
        publishProperties.setEnabled(false);

        publishMetrics.onSuccess(response());
        publishMetrics.status();

        assertThat(logOutput).asString().isEmpty();
    }

    private <T extends Meter> ObjectAssert<T> assertMetric(Iterable<T> meters) {
        return assertThat(meters)
                .hasSize(1)
                .first()
                .returns(NODE_ACCOUNT_ID, t -> t.getId().getTag(PublishMetrics.Tags.TAG_NODE))
                .returns(SCENARIO_NAME, t -> t.getId().getTag(PublishMetrics.Tags.TAG_SCENARIO))
                .returns(TransactionType.CONSENSUS_SUBMIT_MESSAGE.toString(), t -> t.getId()
                        .getTag(PublishMetrics.Tags.TAG_TYPE));
    }

    private PublishRequest request() {
        List<AccountId> nodeAccountIds = List.of(AccountId.fromString(NODE_ACCOUNT_ID));
        return PublishRequest.builder()
                .scenario(publishScenario)
                .timestamp(Instant.now().minusSeconds(5L))
                .transaction(new TopicMessageSubmitTransaction().setNodeAccountIds(nodeAccountIds))
                .build();
    }

    private PublishResponse response() throws Exception {
        TransactionReceipt transactionReceipt = TransactionReceipt.newBuilder().build();
        return PublishResponse.builder()
                .receipt(com.hedera.hashgraph.sdk.TransactionReceipt.fromBytes(transactionReceipt.toByteArray()))
                .request(request())
                .timestamp(Instant.now().minusSeconds(2L))
                .build();
    }
}
