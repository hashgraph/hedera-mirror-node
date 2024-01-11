/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.listener;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
public abstract class SharedTopicListener implements TopicListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ListenerProperties listenerProperties;

    @Override
    @SuppressWarnings("deprecation")
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        DirectProcessor<TopicMessage> overflowProcessor = DirectProcessor.create();
        FluxSink<TopicMessage> overflowSink = overflowProcessor.sink();

        // moving publishOn from after onBackpressureBuffer to after Flux.merge reduces CPU usage by up to 40%
        Flux<TopicMessage> topicMessageFlux = getSharedListener(filter)
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .onBackpressureBuffer(
                        listenerProperties.getMaxBufferSize(), t -> overflowSink.error(Exceptions.failWithOverflow()))
                .doFinally(s -> overflowSink.complete());
        return Flux.merge(listenerProperties.getPrefetch(), topicMessageFlux, overflowProcessor)
                .publishOn(Schedulers.boundedElastic(), false, listenerProperties.getPrefetch());
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);
}
