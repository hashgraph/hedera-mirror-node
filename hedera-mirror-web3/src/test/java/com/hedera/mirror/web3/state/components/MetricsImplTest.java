/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.components;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class MetricsImplTest {

    private MetricsImpl metrics;

    @Mock
    private MetricConfig metricConfig;

    @Mock
    private Metric metric;

    @Mock
    private Runnable runnable;

    @BeforeEach
    void setup() {
        metrics = new MetricsImpl();
    }

    @Test
    void testGetMetric() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.getMetric("", ""));
    }

    @Test
    void testFindMetricsByCategory() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.findMetricsByCategory(""));
    }

    @Test
    void testGetAll() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.getAll());
    }

    @Test
    void testGetOrCreate() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.getOrCreate(metricConfig));
    }

    @Test
    void testRemoveByCategoryAndName() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.remove("", ""));
    }

    @Test
    void testRemoveByMetric() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.remove(metric));
    }

    @Test
    void testRemoveByMetricConfig() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.remove(metricConfig));
    }

    @Test
    void testAddUpdater() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.addUpdater(runnable));
    }

    @Test
    void testRemoveUpdater() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.removeUpdater(runnable));
    }

    @Test
    void testStart() {
        assertThrows(UnsupportedOperationException.class, () -> metrics.start());
    }
}
