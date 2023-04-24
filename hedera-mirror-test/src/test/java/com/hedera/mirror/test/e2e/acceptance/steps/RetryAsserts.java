/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.test.e2e.acceptance.steps;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Retries assertion errors to handle higher level business logic failures that are probably due to timing issues across
 * independent services.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        value = {AssertionError.class},
        backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
        maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RetryAsserts {}
