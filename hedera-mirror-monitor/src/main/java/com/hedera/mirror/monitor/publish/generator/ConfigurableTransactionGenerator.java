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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.properties.ScenarioPropertiesAggregator;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishScenario;
import com.hedera.mirror.monitor.publish.PublishScenarioProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;

@Log4j2
public class ConfigurableTransactionGenerator implements TransactionGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExpressionConverter expressionConverter;
    private final ScenarioPropertiesAggregator scenarioPropertiesAggregator;
    @Getter
    private final PublishScenarioProperties properties;
    private final Supplier<TransactionSupplier<?>> transactionSupplier;
    private final AtomicLong remaining;
    private final long stopTime;
    private final PublishRequest.PublishRequestBuilder builder;
    private final PublishScenario scenario;

    public ConfigurableTransactionGenerator(ExpressionConverter expressionConverter,
                                            ScenarioPropertiesAggregator scenarioPropertiesAggregator,
                                            PublishScenarioProperties properties) {
        this.expressionConverter = expressionConverter;
        this.scenarioPropertiesAggregator = scenarioPropertiesAggregator;
        this.properties = properties;
        transactionSupplier = Suppliers.memoize(this::convert);
        remaining = new AtomicLong(properties.getLimit());
        stopTime = System.nanoTime() + properties.getDuration().toNanos();
        scenario = new PublishScenario(properties);
        builder = PublishRequest.builder().scenario(scenario);
        Assert.state(properties.getRetry().getMaxAttempts() > 0, "maxAttempts must be positive");
    }

    @Override
    public List<PublishRequest> next(int count) {
        if (count <= 0) {
            count = 1;
        }

        long left = remaining.getAndAdd(-count);
        long actual = Math.min(left, count);
        if (actual <= 0) {
            throw new ScenarioException(scenario, "Reached publish limit");
        }

        if (stopTime - System.nanoTime() <= 0) {
            throw new ScenarioException(scenario, "Reached publish duration");
        }

        List<PublishRequest> publishRequests = new ArrayList<>();
        for (long i = 0; i < actual; i++) {
            var transaction = transactionSupplier.get().get()
                    .setMaxAttempts((int) properties.getRetry().getMaxAttempts())
                    .setTransactionMemo(scenario.getMemo());

            PublishRequest publishRequest = builder.receipt(shouldGenerate(properties.getReceiptPercent()))
                    .sendRecord(shouldGenerate(properties.getRecordPercent()))
                    .timestamp(Instant.now())
                    .transaction(transaction)
                    .build();
            publishRequests.add(publishRequest);
        }

        return publishRequests;
    }

    @Override
    public Flux<PublishScenario> scenarios() {
        return Flux.just(scenario);
    }

    private TransactionSupplier<?> convert() {
        Map<String, String> convertedProperties = expressionConverter.convert(properties.getProperties());
        Map<String, Object> correctedProperties = scenarioPropertiesAggregator.aggregateProperties(convertedProperties);
        TransactionSupplier<?> supplier = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(correctedProperties, properties.getType().getSupplier());

        validateSupplier(supplier);

        return supplier;
    }

    private void validateSupplier(TransactionSupplier<?> supplier) {
        Validator validator = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator();
        Set<ConstraintViolation<TransactionSupplier<?>>> validations = validator.validate(supplier);

        if (!validations.isEmpty()) {
            throw new ConstraintViolationException(validations);
        }
    }

    private boolean shouldGenerate(double expectedPercent) {
        return RANDOM.nextDouble() < expectedPercent;
    }
}
