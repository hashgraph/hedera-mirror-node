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

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance.webclient")
@Data
@Validated
public class WebClientProperties {
    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration connectionTimeout = Duration.ofSeconds(10L);

    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration readTimeout = Duration.ofSeconds(10L);

    private boolean wiretap = false;

    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration writeTimeout = Duration.ofSeconds(10L);
}
