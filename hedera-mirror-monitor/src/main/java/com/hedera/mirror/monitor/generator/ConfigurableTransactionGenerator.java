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
    private final Supplier<TransactionSupplier<?>> transactionSupplier;
    private final AtomicLong remaining;
    private final long stopTime;
    private final PublishRequest.PublishRequestBuilder builder;

    public ConfigurableTransactionGenerator(ExpressionConverter expressionConverter, ScenarioProperties properties) {
        this.expressionConverter = expressionConverter;
        this.properties = properties;
        transactionSupplier = Suppliers.memoize(this::convert);
        remaining = new AtomicLong(properties.getLimit());
        stopTime = System.nanoTime() + properties.getDuration().toNanos();
        builder = PublishRequest.builder()
                .logResponse(properties.isLogResponse())
                .scenarioName(properties.getName())
                .type(properties.getType());
    }

    @Override
    public List<PublishRequest> next(int count) {
        if (count <= 0) {
            count = 1;
        }

        long left = remaining.getAndAdd(-count);
        long actual = Math.min(left, count);
        if (actual <= 0) {
            throw new ScenarioException(properties, "Reached publish limit of " + properties.getLimit());
        }

        if (stopTime - System.nanoTime() <= 0) {
            throw new ScenarioException(properties, "Reached publish duration of " + properties.getDuration());
        }

        List<PublishRequest> publishRequests = new ArrayList<>();
        for (long i = 0; i < actual; i++) {
            PublishRequest publishRequest = builder.receipt(shouldGenerate(properties.getReceipt()))
                    .record(shouldGenerate(properties.getRecord()))
                    .timestamp(Instant.now())
                    .transactionBuilder(transactionSupplier.get().get())
                    .build();
            publishRequests.add(publishRequest);
        }

        return publishRequests;
    }

    private TransactionSupplier<?> convert() {
        Map<String, String> convertedProperties = expressionConverter.convert(properties.getProperties());
        TransactionSupplier<?> supplier = new ObjectMapper()
                .convertValue(convertedProperties, properties.getType().getSupplier());

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
