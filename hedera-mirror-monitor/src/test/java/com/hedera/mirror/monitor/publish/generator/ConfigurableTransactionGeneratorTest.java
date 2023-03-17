package com.hedera.mirror.monitor.publish.generator;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.regex.Pattern;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishScenarioProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionType;

class ConfigurableTransactionGeneratorTest {

    private static final int MEMO_SIZE = 32;
    private static final int SAMPLE_SIZE = 10_000;
    private static final String TOPIC_ID = "0.0.1000";

    private PublishScenarioProperties properties;
    private Supplier<ConfigurableTransactionGenerator> generator;

    @BeforeEach
    void init() {
        properties = new PublishScenarioProperties();
        properties.setReceiptPercent(1);
        properties.setRecordPercent(1);
        properties.setMaxMemoLength(MEMO_SIZE);
        properties.setName("test");
        properties.setProperties(Map.of("topicId", TOPIC_ID));
        properties.setTps(100_000);
        properties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        generator = Suppliers.memoize(() -> new ConfigurableTransactionGenerator(p -> p,
                p -> Collections.unmodifiableMap(p), properties));
    }

    @Test
    void nonTruncatedMemo() {
        properties.setMaxMemoLength(100);
        List<PublishRequest> publishRequests = generator.get().next();
        assertThat(publishRequests)
                .isNotEmpty()
                .allSatisfy(publishRequest -> assertThat(publishRequest.getTransaction().getTransactionMemo())
                        .containsPattern(Pattern.compile("\\d+ Monitor test on \\w+"))
                        .hasSizeGreaterThan(MEMO_SIZE));
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
        ConfigurableTransactionGenerator transactionGenerator = generator.get();
        assertRequests(generator.get().next(5), 4);
        assertThatThrownBy(transactionGenerator::next)
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish limit");
    }

    @Test
    void invalidMaxAttempts() {
        properties.getRetry().setMaxAttempts(0L);
        assertThatThrownBy(generator::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("maxAttempts must be positive");
    }

    @Test
    void unknownField() {
        properties.setProperties(Map.of("foo", "bar", "topicId", TOPIC_ID));
        List<PublishRequest> publishRequests = generator.get().next();
        assertThat(publishRequests).hasSize(1); // No error
    }

    @Test
    void reachedLimit() {
        properties.setLimit(1);
        TransactionGenerator transactionGenerator = generator.get();
        assertRequests(transactionGenerator.next());
        assertThatThrownBy(transactionGenerator::next)
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish limit");
    }

    @Test
    void reachedDuration() {
        properties.setDuration(Duration.ofSeconds(-5L));
        ConfigurableTransactionGenerator transactionGenerator = generator.get();
        assertThatThrownBy(transactionGenerator::next)
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
                    .extracting(PublishRequest::isSendRecord)
                    .allMatch(v -> !v);
        }
    }

    @Test
    void recordEnabled() {
        properties.setRecordPercent(1);
        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            assertThat(generator.get().next())
                    .extracting(PublishRequest::isSendRecord)
                    .allMatch(v -> v);
        }
    }

    @Test
    void recordPercent() {
        properties.setRecordPercent(0.75);
        Multiset<Boolean> records = HashMultiset.create();

        for (int i = 0; i < SAMPLE_SIZE; ++i) {
            generator.get().next().forEach(publishRequest -> records.add(publishRequest.isSendRecord()));
        }

        assertThat((double) records.count(true) / SAMPLE_SIZE)
                .isNotNegative()
                .isNotZero()
                .isCloseTo(properties.getRecordPercent(), within(properties.getRecordPercent() * 0.2));
    }

    @Test
    void missingRequiredField() {
        properties.setProperties(Collections.emptyMap());
        ConfigurableTransactionGenerator transactionGenerator = generator.get();
        assertThatThrownBy(transactionGenerator::next).isInstanceOf(ConstraintViolationException.class);
    }

    private void assertRequests(List<PublishRequest> publishRequests, int size) {
        assertThat(publishRequests)
                .hasSize(size)
                .allSatisfy(publishRequest -> assertThat(publishRequest)
                        .isNotNull()
                        .hasNoNullFieldsOrProperties()
                        .hasFieldOrPropertyWithValue("receipt", true)
                        .hasFieldOrPropertyWithValue("sendRecord", true)
                        .hasFieldOrPropertyWithValue("transaction.topicId", TopicId.fromString(TOPIC_ID))
                        .satisfies(r -> assertThat(r.getTransaction().getTransactionMemo())
                                .containsPattern(Pattern.compile("\\d+ Monitor test on \\w+"))
                                .hasSize(MEMO_SIZE))
                );
    }

    private void assertRequests(List<PublishRequest> publishRequests) {
        assertRequests(publishRequests, 1);
    }
}
