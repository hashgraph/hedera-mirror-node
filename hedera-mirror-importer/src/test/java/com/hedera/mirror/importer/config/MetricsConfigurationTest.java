/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.config.MetricsConfiguration.TableMetric;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@RequiredArgsConstructor
class MetricsConfigurationTest extends ImporterIntegrationTest {

    private final MeterRegistry meterRegistry;

    @EnumSource(TableMetric.class)
    @ParameterizedTest
    void partitionedTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "account_balance").gauges()).hasSize(1);
        assertThat(search.tag("table", "account_balance_p2020_01").gauges()).isEmpty();
    }

    @EnumSource(TableMetric.class)
    @ParameterizedTest
    void regularTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "entity").gauges()).hasSize(1);
    }
}
