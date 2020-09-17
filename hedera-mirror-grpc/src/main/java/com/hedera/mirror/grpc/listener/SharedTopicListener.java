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
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.ClientTimeoutException;

public abstract class SharedTopicListener implements TopicListener {

    protected final ListenerProperties listenerProperties;
    protected Flux<TopicMessage> sharedTopicMessages;

    public SharedTopicListener(ListenerProperties listenerProperties) {
        this.listenerProperties = listenerProperties;
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        Duration delay = Duration.ofMillis(listenerProperties.getBufferTimeout());
        UnicastProcessor<String> processor = UnicastProcessor.create();
        Flux<String> timeoutFlux = processor.delayElements(delay)
                .publish()
                .autoConnect();

        return sharedTopicMessages.filter(t -> filterMessage(t, filter))
                .doOnSubscribe(s -> getLogger().info("Subscribing: {}", filter))
                .doOnCancel(() -> processor.onNext("timeout"))
                .onBackpressureBuffer(listenerProperties.getMaxBufferSize())
                .timeout(timeoutFlux, message -> timeoutFlux,
                        Mono.error(new ClientTimeoutException("Client times out to receive the buffered messages")));
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return message.getRealmNum() == filter.getRealmNum() &&
                message.getTopicNum() == filter.getTopicNum() &&
                message.getConsensusTimestamp() >= filter.getStartTimeLong();
    }

    protected abstract Logger getLogger();
}
