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

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.ClientTimeoutException;

@RequiredArgsConstructor
public abstract class SharedTopicListener implements TopicListener {

    protected final Logger log = LogManager.getLogger(getClass());
    protected final ListenerProperties listenerProperties;

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        UnicastProcessor<String> processor = UnicastProcessor.create();
        Flux<String> timeoutFlux = processor.delayElements(listenerProperties.getBufferTimeout())
                .replay(1)
                .autoConnect();

        return getSharedListener(filter).doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .doOnCancel(() -> processor.onNext("timeout"))
                .onBackpressureBuffer(listenerProperties.getMaxBufferSize())
                .timeout(timeoutFlux, message -> timeoutFlux, Mono.error(
                        new ClientTimeoutException("Client timed out while consuming the buffered messages")));
    }

    protected abstract Flux<TopicMessage> getSharedListener(TopicMessageFilter filter);
}
