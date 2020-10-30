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

import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;

import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
@Named
public class CompositeTransactionGenerator implements TransactionGenerator {

    private final EnumeratedDistribution<TransactionGenerator> distribution;

    public CompositeTransactionGenerator(PublishProperties properties) {
        List<Pair<TransactionGenerator, Double>> pairs = new ArrayList<>();

        double total = properties.getScenarios()
                .stream()
                .filter(ScenarioProperties::isEnabled)
                .map(ScenarioProperties::getTps)
                .reduce(0.0, (x, y) -> x + y);

        for (ScenarioProperties scenarioProperties : properties.getScenarios()) {
            if (scenarioProperties.isEnabled()) {
                double weight = total > 0 ? scenarioProperties.getTps() / total : 0.0;
                pairs.add(Pair.create(new ConfigurableTransactionGenerator(scenarioProperties), weight));
            }
        }

        distribution = new EnumeratedDistribution<>(pairs);
    }

    @Override
    public PublishRequest next() {
        try {
            return distribution.sample().next(); // TODO: Remove child supplier when limit or duration is reached
        } catch (Exception e) {
            log.error("Unable to generate a transaction", e);
            throw e;
        }
    }
}
