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

package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderService;
import com.hedera.mirror.importer.parser.ParserProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class HealthCheckConfiguration {

    private final LeaderService leaderService;
    private final MirrorProperties mirrorProperties;
    private final Collection<ParserProperties> parserProperties;

    @Bean
    CompositeHealthContributor streamFileActivity(MeterRegistry meterRegistry) {
        var registry = getRegistry(meterRegistry);
        Map<String, HealthIndicator> healthIndicators = parserProperties.stream()
                .collect(Collectors.toMap(
                        k -> k.getStreamType().toString(),
                        v -> new StreamFileHealthIndicator(leaderService, registry, mirrorProperties, v)));

        return CompositeHealthContributor.fromMap(healthIndicators);
    }

    private MeterRegistry getRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry composite) {
            for (var registry : composite.getRegistries()) {
                if (registry instanceof PrometheusMeterRegistry) {
                    return registry;
                }
            }
        }
        return meterRegistry;
    }
}
