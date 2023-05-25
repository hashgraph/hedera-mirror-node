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

package com.hedera.mirror.test.e2e.acceptance.config;

import com.google.common.base.Throwables;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance.rest")
@Data
@Validated
public class RestPollingProperties {

    public static final String URL_SUFFIX = "/api/v1";

    @NotBlank
    private String baseUrl;

    @Min(1)
    @Max(60)
    private int maxAttempts = 20;

    @NotNull
    @DurationMin(millis = 500L)
    private Duration maxBackoff = Duration.ofSeconds(4L);

    @NotNull
    @DurationMin(millis = 100L)
    private Duration minBackoff = Duration.ofMillis(500L);

    @NotNull
    private Set<Class<?>> retryableExceptions = Set.of(Exception.class);

    public boolean shouldRetry(Throwable t) {
        // Don't retry negative test cases
        if (t instanceof WebClientResponseException wcre && wcre.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return false;
        }
        return retryableExceptions.stream()
                .anyMatch(ex -> ex.isInstance(t) || ex.isInstance(Throwables.getRootCause(t)));
    }

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.endsWith(URL_SUFFIX)) {
            return baseUrl + URL_SUFFIX;
        }
        return baseUrl;
    }
}
