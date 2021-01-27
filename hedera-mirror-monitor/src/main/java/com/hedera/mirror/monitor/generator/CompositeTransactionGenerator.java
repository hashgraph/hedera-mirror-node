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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
@Named
public class CompositeTransactionGenerator implements TransactionGenerator {

    static final Pair<TransactionGenerator, Double> INACTIVE;

    static {
        ScenarioProperties scenarioProperties = new ScenarioProperties();
        scenarioProperties.setName("Inactive");
        scenarioProperties.setProperties(Map.of("topicId", "invalid"));
        scenarioProperties.setTps(Double.MIN_NORMAL); // Never retry
        scenarioProperties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        TransactionGenerator generator = new ConfigurableTransactionGenerator(p -> p, scenarioProperties);
        INACTIVE = Pair.create(generator, 1.0);
    }

    private final ExpressionConverter expressionConverter;
    private final PublishProperties properties;
    volatile EnumeratedDistribution<TransactionGenerator> distribution;

    public CompositeTransactionGenerator(ExpressionConverter expressionConverter, PublishProperties properties) {
        this.expressionConverter = expressionConverter;
        this.properties = properties;
        rebuild();
    }

    @Override
    public PublishRequest next() {
        TransactionGenerator transactionGenerator = distribution.sample();

        try {
            return transactionGenerator.next();
        } catch (ScenarioException e) {
            log.warn(e.getMessage());
            e.getProperties().setEnabled(false);
            rebuild();
            throw e;
        } catch (Exception e) {
            log.error("Unable to generate a transaction", e);
            throw e;
        }
    }

    private synchronized void rebuild() {
        List<Pair<TransactionGenerator, Double>> pairs = new ArrayList<>();

        if (properties.isEnabled()) {
            double total = properties.getScenarios()
                    .stream()
                    .filter(ScenarioProperties::isEnabled)
                    .map(ScenarioProperties::getTps)
                    .reduce(0.0, (x, y) -> x + y);

            for (ScenarioProperties scenarioProperties : properties.getScenarios()) {
                if (scenarioProperties.isEnabled()) {
                    double weight = total > 0 ? scenarioProperties.getTps() / total : 0.0;
                    pairs.add(Pair.create(
                            new ConfigurableTransactionGenerator(expressionConverter, scenarioProperties), weight));
                    log.info("Activated scenario: {}", scenarioProperties);
                }
            }
        }

        if (pairs.isEmpty()) {
            pairs.add(INACTIVE);
            log.info("Publishing is disabled");
        }

        this.distribution = new EnumeratedDistribution<>(pairs);
    }
}
