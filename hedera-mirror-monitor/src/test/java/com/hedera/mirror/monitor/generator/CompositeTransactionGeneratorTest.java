package com.hedera.mirror.monitor.generator;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

public class CompositeTransactionGeneratorTest {

    private PublishProperties properties;
    private Supplier<CompositeTransactionGenerator> supplier;

    @BeforeEach
    void init() {
        ScenarioProperties scenarioProperties1 = new ScenarioProperties();
        scenarioProperties1.setName("test1");
        scenarioProperties1.setProperties(Map.of("topicId", "0.0.1000"));
        scenarioProperties1.setTps(75.0);
        scenarioProperties1.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);

        ScenarioProperties scenarioProperties2 = new ScenarioProperties();
        scenarioProperties2.setName("test2");
        scenarioProperties2.setTps(25.0);
        scenarioProperties2.setType(TransactionType.ACCOUNT_CREATE);

        properties = new PublishProperties();
        properties.getScenarios().add(scenarioProperties1);
        properties.getScenarios().add(scenarioProperties2);
        supplier = Suppliers.memoize(() -> new CompositeTransactionGenerator(properties));
    }

    @Test
    void distribution() {
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.distribution.getPmf())
                .hasSize(properties.getScenarios().size())
                .extracting(Pair::getValue)
                .containsExactly(0.75, 0.25);

        Multiset<TransactionType> types = HashMultiset.create();
        for (int i = 0; i < 100; ++i) {
            PublishRequest request = generator.next();
            types.add(request.getType());
        }

        for (ScenarioProperties scenarioProperties : properties.getScenarios()) {
            assertThat(types.count(scenarioProperties.getType()))
                    .isNotNegative()
                    .isNotZero()
                    .isCloseTo((int) scenarioProperties.getTps(), withinPercentage(40));
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
}
