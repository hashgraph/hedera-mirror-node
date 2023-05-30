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

package com.hedera.mirror.monitor.subscribe;

import com.google.common.collect.Sets;
import com.hedera.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriberProperties;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.monitor.subscribe")
public class SubscribeProperties {

    @Min(1)
    @Max(1024)
    private int clients = 1;

    private boolean enabled = true;

    @NotNull
    private Map<String, GrpcSubscriberProperties> grpc = new LinkedHashMap<>();

    @NotNull
    private Map<String, RestSubscriberProperties> rest = new LinkedHashMap<>();

    @DurationMin(seconds = 1L)
    @NotNull
    protected Duration statusFrequency = Duration.ofSeconds(10L);

    @PostConstruct
    void validate() {
        if (enabled && grpc.isEmpty() && rest.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one subscribe scenario");
        }

        if (Sets.union(grpc.keySet(), rest.keySet()).stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Subscribe scenario name cannot be empty");
        }

        Set<String> names = Sets.intersection(grpc.keySet(), rest.keySet());
        if (!names.isEmpty()) {
            throw new IllegalArgumentException("More than one subscribe scenario with the same name: " + names);
        }

        grpc.forEach((name, property) -> property.setName(name));
        rest.forEach((name, property) -> property.setName(name));
    }
}
