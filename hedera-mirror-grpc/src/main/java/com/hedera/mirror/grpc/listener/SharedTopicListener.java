package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.ClientTimeoutException;

@RequiredArgsConstructor
public abstract class SharedTopicListener implements TopicListener {

    protected final Logger log = LogManager.getLogger(getClass());
    protected final ListenerProperties listenerProperties;

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        TimeoutContext timeoutContext = new TimeoutContext(Schedulers.parallel(),
                listenerProperties.getBufferTimeout());
        Mono<TopicMessage> timeoutMono = Mono.create(timeoutContext);

        // moving publishOn from after onBackpressureBuffer to after Flux.merge reduces CPU usage by up to 40%
        Flux<TopicMessage> topicMessageFlux = getSharedListener(filter)
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .onBackpressureBuffer(listenerProperties.getMaxBufferSize(), t -> timeoutContext.onOverflow())
                .doFinally(r -> timeoutContext.onComplete());
        return Flux.merge(listenerProperties.getPrefetch(), topicMessageFlux, timeoutMono)
                .publishOn(Schedulers.boundedElastic(), false, listenerProperties.getPrefetch());
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);

    @RequiredArgsConstructor
    private class TimeoutContext implements Consumer<MonoSink<TopicMessage>> {

        private final Scheduler scheduler;
        private final Duration timeout;

        private MonoSink<TopicMessage> sink;
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile Disposable taskDisposer;

        @Override
        public void accept(MonoSink<TopicMessage> sink) {
            this.sink = sink;
        }

        public void onComplete() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }

            if (taskDisposer != null) {
                taskDisposer.dispose();
                taskDisposer = null;
            }

            sink.success();
        }

        public void onOverflow() {
            if (completed.get()) {
                return;
            }

            try {
                taskDisposer = scheduler.schedule(() -> sink.error(new ClientTimeoutException(
                                "Client timed out while consuming the buffered messages")),
                        timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                log.error("Failed to schedule task to terminate with ClientTimeoutException", ex);
            }
        }
    }
}
