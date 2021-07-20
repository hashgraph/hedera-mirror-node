package com.hedera.mirror.importer.config;

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

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderService;
import com.hedera.mirror.importer.parser.ParserProperties;

@Configuration
@RequiredArgsConstructor
public class HealthCheckConfiguration {

    private final LeaderService leaderService;
    private final MirrorProperties mirrorProperties;
    private final Collection<ParserProperties> parserProperties;

    @Bean
    CompositeHealthContributor streamFileActivity(@Named("prometheusMeterRegistry") MeterRegistry meterRegistry) {
        Map<String, HealthIndicator> healthIndicators = parserProperties.stream().collect(Collectors.toMap(
                k -> k.getStreamType().toString(),
                v -> new StreamFileHealthIndicator(leaderService, meterRegistry, mirrorProperties, v)));

        return CompositeHealthContributor.fromMap(healthIndicators);
    }
}
