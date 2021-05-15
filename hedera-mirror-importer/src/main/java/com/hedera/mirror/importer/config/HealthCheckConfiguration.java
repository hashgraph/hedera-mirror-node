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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.event.EventParserProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Configuration
@RequiredArgsConstructor
public class HealthCheckConfiguration {
    private final BalanceParserProperties balanceParserProperties;
    private final EventParserProperties eventParserProperties;
    private final RecordParserProperties recordParserProperties;

//    public static final String STREAM_TYPE_BALANCE = "BALANCE";
//    public static final String STREAM_TYPE_EVENT = "EVENT";
//    public static final String STREAM_TYPE_RECORD = "RECORD";
//
//    @Bean(STREAM_TYPE_BALANCE)
//    HealthIndicator balanceStreamHealthIndicator(MeterRegistry meterRegistry) {
//        return new StreamFileHealthIndicator(meterRegistry, STREAM_TYPE_BALANCE, Duration.ofMinutes(30));
//    }
//
//    @Bean(STREAM_TYPE_EVENT)
//    HealthIndicator eventStreamHealthIndicator(MeterRegistry meterRegistry) {
//        return new StreamFileHealthIndicator(meterRegistry, STREAM_TYPE_EVENT, Duration.ofMinutes(0));
//    }
//
//    @Bean(STREAM_TYPE_RECORD)
//    HealthIndicator recordStreamHealthIndicator(MeterRegistry meterRegistry) {
//        return new StreamFileHealthIndicator(meterRegistry, STREAM_TYPE_RECORD, Duration.ofMinutes(5));
//    }

    @Bean
    CompositeHealthContributor streamFileActivity(MeterRegistry meterRegistry) {

        Map<String, HealthIndicator> healthIndicators = new LinkedHashMap<>();

        if (balanceParserProperties.getThresholdWindow() != null) {
            healthIndicators.put(
                    StreamType.BALANCE.toString(),
                    new StreamFileHealthIndicator(
                            meterRegistry,
                            StreamType.BALANCE.toString(),
                            balanceParserProperties.getThresholdWindow()));
        }

        if (eventParserProperties.getThresholdWindow() != null) {
            healthIndicators.put(
                    StreamType.EVENT.toString(),
                    new StreamFileHealthIndicator(
                            meterRegistry,
                            StreamType.EVENT.toString(),
                            eventParserProperties.getThresholdWindow()));
        }

        if (recordParserProperties.getThresholdWindow() != null) {
            healthIndicators.put(
                    StreamType.RECORD.toString(),
                    new StreamFileHealthIndicator(
                            meterRegistry,
                            StreamType.RECORD.toString(),
                            recordParserProperties.getThresholdWindow()));
        }

        return CompositeHealthContributor.fromMap(healthIndicators);
    }
}
