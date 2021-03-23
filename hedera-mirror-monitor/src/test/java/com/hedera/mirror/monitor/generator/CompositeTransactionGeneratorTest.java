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
import static org.assertj.core.api.Assertions.withinPercentage;

import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

class CompositeTransactionGeneratorTest {

    private PublishProperties properties;
    private Supplier<CompositeTransactionGenerator> supplier;
    private double totalTps;

    @BeforeEach
    void init() {
        ScenarioProperties scenarioProperties1 = new ScenarioProperties();
        scenarioProperties1.setName("test1");
        scenarioProperties1.setProperties(Map.of("topicId", "0.0.1000"));
        scenarioProperties1.setTps(750);
        scenarioProperties1.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        totalTps = scenarioProperties1.getTps();

        ScenarioProperties scenarioProperties2 = new ScenarioProperties();
        scenarioProperties2.setName("test2");
        scenarioProperties2.setTps(250);
        scenarioProperties2.setType(TransactionType.ACCOUNT_CREATE);
        totalTps += scenarioProperties2.getTps();

        properties = new PublishProperties();
        properties.getScenarios().add(scenarioProperties1);
        properties.getScenarios().add(scenarioProperties2);
        supplier = Suppliers.memoize(() -> new CompositeTransactionGenerator(p -> p, properties));

        prepare();
    }

    @ParameterizedTest(name = "batch request count {0}")
    @ValueSource(ints = {0, -1})
    void batchRequestDefault(int count) {
        CompositeTransactionGenerator generator = supplier.get();

        List<PublishRequest> publishRequests = generator.next(count);
        assertThat(publishRequests).hasSize(generator.batchSize);
    }

    @Test
    void batchRequestTwo() {
        CompositeTransactionGenerator generator = supplier.get();

        List<PublishRequest> publishRequests = generator.next(2);
        assertThat(publishRequests).hasSize(2);
    }

    @Test
    void batchRequestGreaterThanDefault() {
        CompositeTransactionGenerator generator = supplier.get();
        int count = generator.batchSize + 1;

        List<PublishRequest> publishRequests = generator.next(count);
        assertThat(publishRequests).hasSize(count);
    }

    @Test
    void distribution() {
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.distribution.getPmf())
                .hasSize(properties.getScenarios().size())
                .extracting(Pair::getValue)
                .containsExactly(0.75, 0.25);

        Multiset<TransactionType> types = HashMultiset.create();
        double seconds = 5;
        for (int i = 0; i < totalTps * seconds;) {
            List<PublishRequest> requests = generator.next();
            requests.stream().map(PublishRequest::getType).forEach(types::add);
            i += requests.size();
        }

        for (ScenarioProperties scenarioProperties : properties.getScenarios()) {
            assertThat(types.count(scenarioProperties.getType()))
                    .isNotNegative()
                    .isNotZero()
                    .isCloseTo((int) (scenarioProperties.getTps() * seconds), withinPercentage(10));
        }
    }

    @Test
    void disabledScenario() {
        properties.getScenarios().get(0).setEnabled(false);
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.distribution.getPmf())
                .hasSize(1)
                .extracting(Pair::getValue)
                .containsExactly(1.0);
    }

    @Test
    void noScenario() {
        properties.getScenarios().clear();
        assertInactive();
    }

    @Test
    void noWarmup() {
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();
        Stopwatch stopwatch = Stopwatch.createStarted();
        double seconds = 4.0;
        int total = (int) (totalTps * seconds);
        for (int i = 0; i < total; i++) {
            generator.next();
        }

        assertThat(stopwatch.elapsed().toMillis() * 1.0 / 1000).isCloseTo(seconds, withinPercentage(5));
    }

    @Test
    void publishDisabled() {
        properties.setEnabled(false);
        assertInactive();
    }

    @Test
    void scenariosComplete() {
        properties.getScenarios().remove(properties.getScenarios().size() - 1);
        properties.getScenarios().get(0).setLimit(1L);
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.next()).hasSize(1);
        assertThat(generator.next()).isEmpty();
        assertInactive();
        assertThat(properties.getScenarios())
                .extracting(ScenarioProperties::isEnabled)
                .containsExactly(false);
    }

    @Test
    void warmupBatchRequest() {
        int warmUpSeconds = 4;
        properties.setWarmupPeriod(Duration.ofSeconds(warmUpSeconds));
        CompositeTransactionGenerator generator = supplier.get();

        long begin = System.currentTimeMillis();
        long elapsed = 0;
        long lastElapsed = 0;
        int count = 0;
        List<Integer> counts = new ArrayList<>();
        while (elapsed < (warmUpSeconds + 3) * 1000) {
            List<PublishRequest> publishRequests = generator.next(0);
            count += publishRequests.size();

            elapsed = System.currentTimeMillis() - begin;
            if (elapsed - lastElapsed >= 1000) {
                counts.add(count);
                count = 0;
                lastElapsed = elapsed;
            }
        }

        List<Integer> warmupCounts = counts.subList(0, warmUpSeconds);
        List<Integer> stableCounts = counts.subList(warmUpSeconds, counts.size());
        assertThat(warmupCounts).isSorted().allSatisfy(n -> assertThat(n * 1.0).isLessThan(totalTps));
        assertThat(stableCounts).isNotEmpty()
                .allSatisfy(n -> assertThat(n * 1.0).isCloseTo(totalTps, withinPercentage(5)));
    }

    private void assertInactive() {
        assertThat(supplier.get().rateLimiter.get()).isEqualTo(CompositeTransactionGenerator.INACTIVE_RATE_LIMITER);
    }

    private void prepare() {
        // warmup so in tests the timing will be accurate
        TransactionGenerator generator = Suppliers
                .synchronizedSupplier(() -> new CompositeTransactionGenerator(p -> p, properties)).get();
        generator.next(0);
    }
}
