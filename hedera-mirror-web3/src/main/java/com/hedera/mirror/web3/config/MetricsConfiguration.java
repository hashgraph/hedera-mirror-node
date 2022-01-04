package com.hedera.mirror.web3.config;

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

import com.google.common.collect.Sets;
import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.github.mweirauch.micrometer.jvm.extras.ProcessThreadMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.web.mappings.reactive.DispatcherHandlerMappingDescription;
import org.springframework.boot.actuate.web.mappings.reactive.DispatcherHandlerMappingDetails;
import org.springframework.boot.actuate.web.mappings.reactive.DispatcherHandlersMappingDescriptionProvider;
import org.springframework.boot.actuate.web.mappings.reactive.RequestMappingConditionsDescription;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MetricsConfiguration {

    @Bean
    MeterBinder processMemoryMetrics() {
        return new ProcessMemoryMetrics();
    }

    @Bean
    MeterBinder processThreadMetrics() {
        return new ProcessThreadMetrics();
    }

    @Bean
    NettyServerCustomizer nettyServerCustomizer(ApplicationContext applicationContext) {
        var provider = new DispatcherHandlersMappingDescriptionProvider();
        Set<String> patterns = provider.describeMappings(applicationContext)
                .values()
                .stream()
                .flatMap(List::stream)
                .map(DispatcherHandlerMappingDescription::getDetails)
                .filter(Objects::nonNull)
                .map(DispatcherHandlerMappingDetails::getRequestMappingConditions)
                .map(RequestMappingConditionsDescription::getPatterns)
                .flatMap(Set::stream)
                .filter(s -> !s.contains("*"))
                .collect(Collectors.toSet());

        Set<String> routes = Sets.union(patterns, Set.of("/actuator/health/liveness", "/actuator/health/readiness"));
        final String unknownPath = "unknown";

        return n -> n.metrics(true, route -> routes.contains(route) ? route : unknownPath);
    }
}
