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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        properties.setReceiptPercent(1);
        properties.setRecordPercent(1);
        properties.setName("test");
        properties.setProperties(Map.of("topicId", TOPIC_ID));
        properties.setTps(100_000);
        properties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        generator = Suppliers.memoize(() -> new ConfigurableTransactionGenerator(p -> p, p -> p.entrySet().stream()
                .collect(Collectors
                        .toMap(Map.Entry::getKey, e -> e.getValue())), properties));
    }

    @Test
    void next() {
        assertRequests(generator.get().next());
    }

    @ParameterizedTest(name = "next with count {0}")
    @ValueSource(ints = {0, -1})
    void nextDefault(int count) {
        assertRequests(generator.get().next(count));
    }

    @Test
    void nextTwo() {
        assertRequests(generator.get().next(2), 2);
    }

    @Test
    void nextCountMoreThanLimit() {
        properties.setLimit(4);
        assertRequests(generator.get().next(5), 4);
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish limit");
    }

    @Test
    void logResponse() {
        properties.setLogResponse(true);
        assertRequests(generator.get().next());
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
        TransactionGenerator transactionGenerator = generator.get();
        assertRequests(transactionGenerator.next());
        assertThatThrownBy(() -> transactionGenerator.next())
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
        properties.setReceiptPercent(0);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isReceipt)
                    .allMatch(v -> !v);
        }
    }

    @Test
    void receiptEnabled() {
        properties.setReceiptPercent(1);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isReceipt)
                    .allMatch(v -> v);
        }
    }

    @Test
    void receiptPercent() {
        properties.setReceiptPercent(0.1);
        Multiset<Boolean> receipts = HashMultiset.create();

        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            generator.get().next().forEach(publishRequest -> receipts.add(publishRequest.isReceipt()));
        }

        assertThat((double) receipts.count(true) / SAMPLE_SIZE)
                .isNotNegative()
                .isNotZero()
                .isCloseTo(properties.getReceiptPercent(), within(properties.getReceiptPercent() * 0.2));
    }

    @Test
    void recordDisabled() {
        properties.setRecordPercent(0);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isRecord)
                    .allMatch(v -> !v);
        }
    }

    @Test
    void recordEnabled() {
        properties.setRecordPercent(1);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isRecord)
                    .allMatch(v -> v);
        }
    }

    @Test
    void recordPercent() {
        properties.setRecordPercent(0.75);
        Multiset<Boolean> records = HashMultiset.create();

        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            generator.get().next().forEach(publishRequest -> records.add(publishRequest.isRecord()));
        }

        assertThat((double) records.count(true) / SAMPLE_SIZE)
                .isNotNegative()
                .isNotZero()
                .isCloseTo(properties.getRecordPercent(), within(properties.getRecordPercent() * 0.2));
    }

    @Test
    void missingRequiredField() {
        properties.setProperties(Collections.emptyMap());
        assertThatThrownBy(() -> generator.get().next()).isInstanceOf(ConstraintViolationException.class);
    }

    private void assertRequests(List<PublishRequest> publishRequests, int size) {
        assertThat(publishRequests).hasSize(size).allSatisfy(publishRequest -> assertThat(publishRequest)
                .isNotNull()
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("logResponse", properties.isLogResponse())
                .hasFieldOrPropertyWithValue("receipt", true)
                .hasFieldOrPropertyWithValue("record", true)
                .hasFieldOrPropertyWithValue("type", properties.getType())
                .hasFieldOrPropertyWithValue("transaction.topicId", TopicId.fromString(TOPIC_ID))
        );
    }

    private void assertRequests(List<PublishRequest> publishRequests) {
        assertRequests(publishRequests, 1);
    }
}
