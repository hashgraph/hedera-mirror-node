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
import io.micrometer.core.instrument.search.Search;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.event.EventParserProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Configuration
@RequiredArgsConstructor
public class HealthCheckConfiguration {
    private final BalanceParserProperties balanceParserProperties;
    private final EventParserProperties eventParserProperties;
    private final MirrorProperties mirrorProperties;
    private final RecordParserProperties recordParserProperties;

    @Bean
    CompositeHealthContributor streamFileActivity(MeterRegistry meterRegistry) {
        Search searchTimer = meterRegistry.find("hedera.mirror.parse.duration");
        Map<String, HealthIndicator> healthIndicators = new LinkedHashMap<>();

        if (balanceParserProperties.getStreamFileStatusCheckWindow() != null) {
            healthIndicators.put(
                    StreamType.BALANCE.toString(),
                    new StreamFileHealthIndicator(
                            searchTimer.tag("type", StreamType.BALANCE.toString()).timer(),
                            balanceParserProperties.getStreamFileStatusCheckWindow(),
                            mirrorProperties.getEndDate()));
        }

        if (eventParserProperties.getStreamFileStatusCheckWindow() != null) {
            healthIndicators.put(
                    StreamType.EVENT.toString(),
                    new StreamFileHealthIndicator(
                            searchTimer.tag("type", StreamType.EVENT.toString()).timer(),
                            eventParserProperties.getStreamFileStatusCheckWindow(),
                            mirrorProperties.getEndDate()));
        }

        if (recordParserProperties.getStreamFileStatusCheckWindow() != null) {
            healthIndicators.put(
                    StreamType.RECORD.toString(),
                    new StreamFileHealthIndicator(
                            searchTimer.tag("type", StreamType.RECORD.toString()).timer(),
                            recordParserProperties.getStreamFileStatusCheckWindow(),
                            mirrorProperties.getEndDate()));
        }

        return CompositeHealthContributor.fromMap(healthIndicators);
    }
}
