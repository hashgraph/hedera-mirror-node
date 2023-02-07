package com.hedera.mirror.importer.downloader.provider;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import com.google.common.annotations.VisibleForTesting;
import lombok.CustomLog;
import lombok.Value;
import org.springframework.context.annotation.Primary;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.StreamSourceProperties;

@CustomLog
@Named
@Primary
final class CompositeStreamFileProvider implements StreamFileProvider {

    private final List<ProviderHealth> providers;

    public CompositeStreamFileProvider(CommonDownloaderProperties properties, List<StreamFileProvider> providers) {
        var providerHealth = new ArrayList<ProviderHealth>();

        for (int i = 0; i < providers.size(); ++i) {
            var provider = providers.get(i);
            var sourceProperties = properties.getSources().get(i);
            providerHealth.add(new ProviderHealth(provider, sourceProperties));
        }

        this.providers = Collections.unmodifiableList(providerHealth);
    }

    @Override
    public Mono<StreamFileData> get(ConsensusNode consensusNode, StreamFilename streamFilename) {
        var index = new AtomicInteger(0);
        return Mono.fromSupplier(() -> getProvider(index))
                .flatMap(p -> p.get(consensusNode, streamFilename))
                .retryWhen(Retry.from(s -> s.map(r -> shouldRetry(r, index))));
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode consensusNode, StreamFilename lastFilename) {
        var index = new AtomicInteger(0);
        return Mono.fromSupplier(() -> getProvider(index))
                .flatMapMany(p -> p.list(consensusNode, lastFilename))
                .retryWhen(Retry.from(s -> s.map(r -> shouldRetry(r, index))));
    }

    // Get the next healthy provider
    private StreamFileProvider getProvider(AtomicInteger index) {
        for (; index.get() < providers.size(); index.getAndIncrement()) {
            var provider = providers.get(index.get());

            if (provider.isHealthy()) {
                return provider.getProvider();
            }
        }

        return null;
    }

    private boolean shouldRetry(Retry.RetrySignal r, AtomicInteger index) {
        var exception = r.failure();
        log.warn("Attempt #{} failed: {}", r.totalRetries() + 1, exception.getMessage());

        if (exception instanceof TransientProviderException t) {
            throw t;
        }

        // Ensure we always keep at least one provider available
        if (index.get() + 1 >= providers.size()) {
            throw Exceptions.propagate(exception);
        }

        var provider = providers.get(index.getAndIncrement());
        provider.markUnhealthy();
        return true;
    }

    @VisibleForTesting
    boolean isHealthy() {
        return providers.stream().filter(ProviderHealth::isHealthy).map(ProviderHealth::isHealthy).findFirst().orElse(false);
    }

    @Value
    private class ProviderHealth {

        private final StreamFileProvider provider;
        private final StreamSourceProperties sourceProperties;
        private final AtomicLong readmitTime = new AtomicLong(0L); // Zero indicates healthy

        /**
         * Determines if the provider is healthy. This has the side effect of marking an unhealthy provider as healthy
         * again if its readmit time has passed.
         *
         * @return whether the provider is healthy
         */
        boolean isHealthy() {
            long readmitMillis = readmitTime.get();

            if (readmitMillis > 0 && readmitMillis <= System.currentTimeMillis()) {
                readmitTime.compareAndSet(readmitMillis, 0L);
                return true;
            }

            return readmitMillis == 0;
        }

        void markUnhealthy() {
            long backoff = sourceProperties.getBackoff().toMillis();
            readmitTime.set(System.currentTimeMillis() + backoff);
        }
    }
}
