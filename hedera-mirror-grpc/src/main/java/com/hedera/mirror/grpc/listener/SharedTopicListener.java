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
        TimeoutContext timeoutContext = new TimeoutContext(Schedulers.parallel(), listenerProperties.getBufferTimeout());
        Mono<TopicMessage> timeoutMono = Mono.create(timeoutContext);

        Flux<TopicMessage> topicMessageFlux = getSharedListener(filter)
                .publishOn(Schedulers.boundedElastic())
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .doOnCancel(timeoutContext::timeout)
                .onBackpressureBuffer(listenerProperties.getMaxBufferSize())
                .doOnCancel(timeoutContext::onComplete)
                .doOnComplete(timeoutContext::onComplete);
        return Flux.merge(1, topicMessageFlux, timeoutMono);
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);


    @RequiredArgsConstructor
    private class TimeoutContext implements Consumer<MonoSink<TopicMessage>> {

        private final Scheduler scheduler;
        private final Duration timeoutDuration;

        private MonoSink<TopicMessage> sink;
        private boolean completed;
        private Disposable taskDisposer;

        @Override
        public void accept(final MonoSink<TopicMessage> sink) {
            this.sink = sink;
        }

        public void timeout() {
            if (completed) {
                return;
            }

            try {
                taskDisposer = scheduler.schedule(() -> {
                    sink.error(new ClientTimeoutException("Client timed out while consuming the buffered messages"));
                }, timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                log.error("Failed to schedule task to send ClientTimeoutException", ex);
            }
        }

        public void onComplete() {
            completed = true;

            if (taskDisposer != null) {
                taskDisposer.dispose();
            }

            sink.success();
        }
    }
}
