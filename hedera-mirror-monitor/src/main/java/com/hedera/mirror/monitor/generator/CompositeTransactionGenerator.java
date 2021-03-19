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

import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;

import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
@Named
public class CompositeTransactionGenerator implements TransactionGenerator {

    static final RateLimiter INACTIVE_RATE_LIMITER;

    static {
        INACTIVE_RATE_LIMITER = RateLimiter.create(Double.MIN_NORMAL);
        // the first acquire always succeeds, so do this so tps=Double.MIN_NORMAL won't acquire
        INACTIVE_RATE_LIMITER.acquire();
    }

    private final ExpressionConverter expressionConverter;
    private final PublishProperties properties;
    volatile EnumeratedDistribution<TransactionGenerator> distribution;
    AtomicReference<RateLimiter> rateLimiter = new AtomicReference<>();
    volatile int batchSize;

    public CompositeTransactionGenerator(ExpressionConverter expressionConverter, PublishProperties properties) {
        this.expressionConverter = expressionConverter;
        this.properties = properties;
        rebuild();
    }

    @Override
    public List<PublishRequest> next(int count) {
        int permits = count > 0 ? count : batchSize;
        rateLimiter.get().acquire(permits);

        List<PublishRequest> publishRequests = new ArrayList<>();
        int i = 0;
        while (i < permits) {
            try {
                TransactionGenerator transactionGenerator = distribution.sample();
                publishRequests.addAll(transactionGenerator.next());
                i++;
            } catch (ScenarioException e) {
                log.warn(e.getMessage());
                e.getProperties().setEnabled(false);
                rebuild();
                if (rateLimiter.get().equals(INACTIVE_RATE_LIMITER)) {
                    break;
                }
            } catch (Exception e) {
                log.error("Unable to generate a transaction", e);
                throw e;
            }
        }

        return publishRequests;
    }

    private synchronized void rebuild() {
        List<ScenarioProperties> enabledScenarios = properties.getScenarios()
                .stream()
                .filter(ScenarioProperties::isEnabled)
                .collect(Collectors.toList());
        if (!properties.isEnabled() || enabledScenarios.isEmpty()) {
            batchSize = 1;
            distribution = null;
            rateLimiter.set(INACTIVE_RATE_LIMITER);
            log.info("Publishing is disabled");
            return;
        }

        List<Pair<TransactionGenerator, Double>> pairs = new ArrayList<>();
        final double total = enabledScenarios
                .stream()
                .map(ScenarioProperties::getTps)
                .reduce(0.0, Double::sum);
        enabledScenarios.forEach(scenarioProperties -> {
            double weight = total > 0 ? scenarioProperties.getTps() / total : 0.0;
            pairs.add(Pair.create(new ConfigurableTransactionGenerator(expressionConverter, scenarioProperties), weight));
            log.info("Activated scenario: {}", scenarioProperties);
        });

        batchSize = Math.max(1, (int) Math.ceil(total / properties.getBatchDivisor()));

        RateLimiter current = rateLimiter.get();
        if (current != null) {
            current.setRate(total);
        } else {
            rateLimiter.set(getRateLimiter(total, properties.getWarmupPeriod()));
        }

        distribution = new EnumeratedDistribution<>(pairs);
    }

    private RateLimiter getRateLimiter(double tps, Duration warmupPeriod) {
        if (warmupPeriod.equals(Duration.ZERO)) {
            return RateLimiter.create(tps);
        }

        return RateLimiter.create(tps, properties.getWarmupPeriod());
    }
}
