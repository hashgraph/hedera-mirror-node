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

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.Metrics;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

public class MetricsImpl implements Metrics {

    @Nullable
    @Override
    public Metric getMetric(@Nonnull String category, @Nonnull String name) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Collection<Metric> findMetricsByCategory(@Nonnull String category) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Collection<Metric> getAll() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T extends Metric> T getOrCreate(@Nonnull MetricConfig<T, ?> config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@Nonnull String category, @Nonnull String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@Nonnull Metric metric) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@Nonnull MetricConfig<?, ?> config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addUpdater(@Nonnull Runnable updater) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeUpdater(@Nonnull Runnable updater) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }
}
