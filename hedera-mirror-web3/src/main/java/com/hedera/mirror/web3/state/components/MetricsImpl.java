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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;

public class MetricsImpl implements Metrics {

    @Nullable
    @Override
    public Metric getMetric(@NonNull String category, @NonNull String name) {
        return null;
    }

    @NonNull
    @Override
    public Collection<Metric> findMetricsByCategory(@NonNull String category) {
        return null;
    }

    @NonNull
    @Override
    public Collection<Metric> getAll() {
        return null;
    }

    @NonNull
    @Override
    public <T extends Metric> T getOrCreate(@NonNull MetricConfig<T, ?> config) {
        return null;
    }

    @Override
    public void remove(@NonNull String category, @NonNull String name) {}

    @Override
    public void remove(@NonNull Metric metric) {}

    @Override
    public void remove(@NonNull MetricConfig<?, ?> config) {}

    @Override
    public void addUpdater(@NonNull Runnable updater) {}

    @Override
    public void removeUpdater(@NonNull Runnable updater) {}

    @Override
    public void start() {}
}
