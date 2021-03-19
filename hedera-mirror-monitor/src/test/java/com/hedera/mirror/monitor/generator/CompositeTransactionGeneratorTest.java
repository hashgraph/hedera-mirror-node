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
import static org.assertj.core.api.Assertions.withinPercentage;

import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
class CompositeTransactionGeneratorTest {

//    private PublishProperties properties;
//    private Supplier<CompositeTransactionGenerator> supplier;
//    private double totalTps;
//
//    @BeforeEach
//    void init() {
//        double tps = 750;
//        ScenarioProperties scenarioProperties1 = new ScenarioProperties();
//        scenarioProperties1.setName("test1");
//        scenarioProperties1.setProperties(Map.of("topicId", "0.0.1000"));
//        scenarioProperties1.setTps(tps);
//        scenarioProperties1.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
//        totalTps = tps;
//
//        tps = 250;
//        ScenarioProperties scenarioProperties2 = new ScenarioProperties();
//        scenarioProperties2.setName("test2");
//        scenarioProperties2.setTps(tps);
//        scenarioProperties2.setType(TransactionType.ACCOUNT_CREATE);
//        totalTps += tps;
//
//        properties = new PublishProperties();
//        properties.getScenarios().add(scenarioProperties1);
//        properties.getScenarios().add(scenarioProperties2);
//        supplier = Suppliers.memoize(() -> new CompositeTransactionGenerator(p -> p, properties));
//    }
//
//    @Test
//    void distribution() {
//        properties.setWarmupPeriod(Duration.ZERO);
//        CompositeTransactionGenerator generator = supplier.get();
//        assertThat(generator.distribution.getPmf())
//                .hasSize(properties.getScenarios().size())
//                .extracting(Pair::getValue)
//                .containsExactly(0.75, 0.25);
//
//        Multiset<TransactionType> types = HashMultiset.create();
//        double seconds = 5;
//        for (int i = 0; i < totalTps * seconds; ++i) {
//            PublishRequest request = generator.next();
//            types.add(request.getType());
//        }
//
//        for (ScenarioProperties scenarioProperties : properties.getScenarios()) {
//            assertThat(types.count(scenarioProperties.getType()))
//                    .isNotNegative()
//                    .isNotZero()
//                    .isCloseTo((int) (scenarioProperties.getTps() * seconds), withinPercentage(10));
//        }
//    }
//
//    @Test
//    void disabledScenario() {
//        properties.getScenarios().get(0).setEnabled(false);
//        CompositeTransactionGenerator generator = supplier.get();
//        assertThat(generator.distribution.getPmf())
//                .hasSize(1)
//                .extracting(Pair::getValue)
//                .containsExactly(1.0);
//    }
//
//    @Test
//    void noScenario() {
//        properties.getScenarios().clear();
//        assertInactive();
//    }
//
//    @Test
//    void noWarmup() {
//        properties.setWarmupPeriod(Duration.ZERO);
//        CompositeTransactionGenerator generator = supplier.get();
//        Stopwatch stopwatch = Stopwatch.createStarted();
//        double seconds = 4.0;
//        int total = (int) (totalTps * seconds);
//        for (int i = 0; i < total; i++) {
//            generator.next();
//        }
//
//        assertThat(stopwatch.elapsed().toMillis() * 1.0 / 1000).isCloseTo(seconds, withinPercentage(5));
//    }
//
//    @Test
//    void publishDisabled() {
//        properties.setEnabled(false);
//        assertInactive();
//    }
//
//    @Test
//    void scenariosComplete() {
//        properties.getScenarios().remove(properties.getScenarios().size() - 1);
//        properties.getScenarios().get(0).setLimit(1L);
//        CompositeTransactionGenerator generator = supplier.get();
//        assertThat(generator.next()).isNotNull();
//        assertThatThrownBy(() -> generator.next()).isInstanceOf(ScenarioException.class);
//        assertInactive();
//        assertThat(properties.getScenarios())
//                .extracting(ScenarioProperties::isEnabled)
//                .containsExactly(false);
//    }
//
//    private void assertInactive() {
//        assertThat(supplier.get().rateLimiter).isEqualTo(CompositeTransactionGenerator.INACTIVE_RATE_LIMITER);
//    }
//
//    private double getTps(int count, Duration elapsed) {
//        return count * 1.0 / (elapsed.toNanos() * 1.0 / 1_000_000_000L);
//    }
}
