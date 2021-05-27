package com.hedera.mirror.monitor.subscribe;

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
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriberProperties;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.monitor.subscribe")
public class SubscribeProperties {

    @Min(1)
    @Max(1024)
    private int clients = 1;

    private boolean enabled = true;

    @NotNull
    private List<GrpcSubscriberProperties> grpc = new ArrayList<>();

    @NotNull
    private List<RestSubscriberProperties> rest = new ArrayList<>();

    @DurationMin(seconds = 1L)
    @NotNull
    protected Duration statusFrequency = Duration.ofSeconds(10L);

    @PostConstruct
    void validate() {
        if (enabled && grpc.isEmpty() && rest.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one subscribe scenario");
        }

        Set<String> names = Stream.concat(grpc.stream(), rest.stream())
                .map(AbstractSubscriberProperties::getName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!names.isEmpty()) {
            throw new IllegalArgumentException("More than one subscribe scenario with the same name: " + names);
        }
    }
}
