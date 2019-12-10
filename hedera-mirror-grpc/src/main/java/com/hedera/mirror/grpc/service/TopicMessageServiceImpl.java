package com.hedera.mirror.grpc.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.listener.TopicListener;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;

@Named
@Log4j2
@RequiredArgsConstructor
@Validated
public class TopicMessageServiceImpl implements TopicMessageService {

    private final TopicListener topicListener;
    private final TopicMessageRepository topicMessageRepository;

    @Override
    public Flux<TopicMessage> subscribeTopic(TopicMessageFilter filter) {
        log.info("Subscribing to topic: {}", filter);
        TopicContext topicContext = new TopicContext();

        // setup incoming messages flow
        Flux<TopicMessage> incomingMessages = topicListener.listen(filter)
                .filter(t -> t.getSequenceNumber() > topicContext.getLastSequenceNumber())
                .bufferUntil(t -> topicContext.isQueryComplete())
                .flatMapIterable(t -> t);

        // collect historical messages
        Flux<TopicMessage> repositoryMessages = topicMessageRepository.findByFilter(filter)
                .doOnNext(t -> topicContext.setLastSequenceNumber(t.getSequenceNumber()))
                .doOnComplete(() -> topicContext.setQueryComplete(true))

        incomingMessages.subscribe();

        // To:do - determine if any missing messages exist.
        // Compare the first sequence number of the notification and the last from the repository call.
        // If there's a gap then make a db call for topic messages of the given sequence number range
        // note items may not be in oder so maybe find the max of the db call and the min of the notifications
        // this will have to be done lazily because you may not have a notification to use. Maybe use a doFirst() /
        // doOnSubscribe

        return Flux.concat(repositoryMessages, incomingMessages)
                .as(t -> filter.hasLimit() ? t.limitRequest(filter.getLimit()) : t);
    }

    @Data
    private class TopicContext {
        private volatile boolean queryComplete = false;
        private volatile Long lastSequenceNumber = Long.MAX_VALUE;
    }
}
