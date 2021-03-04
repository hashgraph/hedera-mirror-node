package com.hedera.mirror.monitor.generator;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.mirror.monitor.publish.PublishRequest;

class ConfigurableTransactionGeneratorTest {

    private static final int SAMPLE_SIZE = 10_000;
    private static final String TOPIC_ID = "0.0.1000";

    private ScenarioProperties properties;
    private Supplier<ConfigurableTransactionGenerator> generator;

    @BeforeEach
    void init() {
        properties = new ScenarioProperties();
        properties.setReceipt(1);
        properties.setRecord(1);
        properties.setName("test");
        properties.setProperties(Map.of("topicId", TOPIC_ID));
        properties.setTps(100_000);
        properties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        generator = Suppliers.memoize(() -> new ConfigurableTransactionGenerator(p -> p, properties));
    }

    @Test
    void next() {
        assertRequest(generator.get().next());
    }

    @Test
    void logResponse() {
        properties.setLogResponse(true);
        assertRequest(generator.get().next());
    }

    @Test
    void unknownField() {
        properties.setProperties(Map.of("foo", "bar", "topicId", TOPIC_ID));
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized field");
    }

    @Test
    void reachedLimit() {
        properties.setLimit(1);
        assertRequest(generator.get().next());
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish limit");
    }

    @Test
    void reachedDuration() {
        properties.setDuration(Duration.ofSeconds(-5L));
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish duration");
    }

    @Test
    void receiptDisabled() {
        properties.setReceipt(0);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isReceipt)
                    .isEqualTo(false);
        }
    }

    @Test
    void receiptEnabled() {
        properties.setReceipt(1);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isReceipt)
                    .isEqualTo(true);
        }
    }

    @Test
    void receiptPercent() {
        properties.setReceipt(0.1);
        Multiset<Boolean> receipts = HashMultiset.create();

        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            receipts.add(generator.get().next().isReceipt());
        }

        assertThat((double) receipts.count(true) / SAMPLE_SIZE)
                .isNotNegative()
                .isNotZero()
                .isCloseTo(properties.getReceipt(), within(properties.getReceipt() * 0.2));
    }

    @Test
    void recordDisabled() {
        properties.setRecord(0);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isRecord)
                    .isEqualTo(false);
        }
    }

    @Test
    void recordEnabled() {
        properties.setRecord(1);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isRecord)
                    .isEqualTo(true);
        }
    }

    @Test
    void recordPercent() {
        properties.setRecord(0.75);
        Multiset<Boolean> records = HashMultiset.create();

        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            records.add(generator.get().next().isRecord());
        }

        assertThat((double) records.count(true) / SAMPLE_SIZE)
                .isNotNegative()
                .isNotZero()
                .isCloseTo(properties.getRecord(), within(properties.getRecord() * 0.2));
    }

    @Test
    void missingRequiredField() {
        properties.setProperties(Collections.emptyMap());
        assertThatThrownBy(() -> generator.get().next()).isInstanceOf(ConstraintViolationException.class);
    }

    private void assertRequest(PublishRequest publishRequest) {
        assertThat(publishRequest).isNotNull()
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("logResponse", properties.isLogResponse())
                .hasFieldOrPropertyWithValue("receipt", true)
                .hasFieldOrPropertyWithValue("record", true)
                .hasFieldOrPropertyWithValue("type", properties.getType())
                .hasFieldOrPropertyWithValue("transactionBuilder.topicId", TopicId.fromString(TOPIC_ID));
    }
}
