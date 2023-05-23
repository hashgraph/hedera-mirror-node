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

package com.hedera.mirror.monitor.publish;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.monitor.publish")
public class PublishProperties {

    private boolean async = true;

    @Min(100)
    private int batchDivisor = 100;

    @Min(1)
    private int clients = 4;

    private boolean enabled = true;

    @NotNull
    private Duration nodeMaxBackoff = Duration.ofMinutes(1L);

    @NotNull
    private Map<String, PublishScenarioProperties> scenarios = new LinkedHashMap<>();

    @DurationMin(seconds = 1L)
    @NotNull
    private Duration statusFrequency = Duration.ofSeconds(10L);

    @Min(1)
    private int responseThreads = 40;

    @DurationMin(seconds = 0)
    @NotNull
    private Duration warmupPeriod = Duration.ofSeconds(30L);

    @PostConstruct
    void validate() {
        if (enabled && scenarios.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one publish scenario");
        }

        if (scenarios.keySet().stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Publish scenario name cannot be empty");
        }

        scenarios.forEach((name, property) -> property.setName(name));
    }
}
