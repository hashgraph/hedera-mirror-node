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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import lombok.extern.log4j.Log4j2;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishRequest;

@Log4j2
public class ConfigurableTransactionGenerator implements TransactionGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExpressionConverter expressionConverter;
    private final ScenarioProperties properties;
    private final TransactionSupplier<?> transactionSupplier;
    private final RateLimiter rateLimiter;
    private final AtomicLong remaining;
    private final long stopTime;
    private final PublishRequest.PublishRequestBuilder builder;

    public ConfigurableTransactionGenerator(ExpressionConverter expressionConverter, ScenarioProperties properties) {
        this.expressionConverter = expressionConverter;
        this.properties = properties;
        this.transactionSupplier = convert(properties);
        this.rateLimiter = RateLimiter.create(properties.getTps());
        remaining = new AtomicLong(properties.getLimit());
        stopTime = System.nanoTime() + properties.getDuration().toNanos();
        builder = PublishRequest.builder()
                .logResponse(properties.isLogResponse())
                .scenarioName(properties.getName())
                .type(properties.getType());
        rateLimiter.acquire(); // The first acquire always succeeds, so do this so tps=Double.MIN_NORMAL won't acquire
    }

    @Override
    public PublishRequest next() {
        rateLimiter.acquire();
        long count = remaining.getAndDecrement();

        if (count <= 0) {
            throw new ScenarioException(properties, "Reached publish limit of " + properties.getLimit());
        }

        if (stopTime - System.nanoTime() <= 0) {
            throw new ScenarioException(properties, "Reached publish duration of " + properties.getDuration());
        }

        return builder.receipt(shouldGenerate(properties.getReceipt()))
                .record(shouldGenerate(properties.getRecord()))
                .timestamp(Instant.now())
                .transactionBuilder(transactionSupplier.get())
                .build();
    }

    private TransactionSupplier<?> convert(ScenarioProperties p) {
        Map<String, String> properties = expressionConverter.convert(p.getProperties());
        TransactionSupplier<?> supplier = new ObjectMapper().convertValue(properties, p.getType().getSupplier());

        Validator validator = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator();
        Set<ConstraintViolation<TransactionSupplier<?>>> validations = validator.validate(supplier);

        if (!validations.isEmpty()) {
            throw new ConstraintViolationException(validations);
        }

        return supplier;
    }

    private boolean shouldGenerate(double expectedPercent) {
        return RANDOM.nextDouble() < expectedPercent;
    }
}
