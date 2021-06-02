package com.hedera.mirror.monitor.publish;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.monitor.generator.ScenarioProperties;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.monitor.publish")
public class PublishProperties {

    @Min(100)
    private int batchDivisor = 100;

    @Min(1)
    private int clients = 4;

    private boolean enabled = true;

    @NotNull
    private List<ScenarioProperties> scenarios = new ArrayList<>();

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

        Set<String> names = scenarios.stream()
                .map(ScenarioProperties::getName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!names.isEmpty()) {
            throw new IllegalArgumentException("More than one publish scenario with the same name: " + names);
        }
    }
}
