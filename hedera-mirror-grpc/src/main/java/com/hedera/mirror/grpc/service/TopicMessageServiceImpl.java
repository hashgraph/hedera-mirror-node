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

import com.google.common.base.Stopwatch;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        TopicContext topicContext = new TopicContext(filter);
        return topicMessageRepository.findByFilter(filter)
                .doOnComplete(topicContext::onComplete)
                .concatWith(incomingMessages(topicContext))
                .filter(t -> t.compareTo(topicContext.getLastTopicMessage()) > 0) // Ignore duplicates
                .takeWhile(t -> filter.getEndTime() == null || t.getConsensusTimestamp().isBefore(filter.getEndTime()))
                .as(t -> filter.hasLimit() ? t.limitRequest(filter.getLimit()) : t)
                .doOnNext(topicContext::onNext)
                .doOnComplete(topicContext::onComplete);
    }

    private Flux<TopicMessage> incomingMessages(TopicContext topicContext) {
        return topicListener.listen(topicContext.getFilter())
                .flatMap(t -> topicContext.isNext(t) ? Mono.just(t) : missingMessages(topicContext, t));
    }

    private Flux<TopicMessage> missingMessages(TopicContext topicContext, TopicMessage current) {
        TopicMessage last = topicContext.getLastTopicMessage();
        TopicMessageFilter filter = topicContext.getFilter();
        TopicMessageFilter newFilter = TopicMessageFilter.builder()
                .endTime(current.getConsensusTimestamp())
                .limit(current.getSequenceNumber() - last.getSequenceNumber() - 1)
                .realmNum(filter.getRealmNum())
                .startTime(last.getConsensusTimestamp().plusNanos(1))
                .topicNum(filter.getTopicNum())
                .build();

        log.info("Querying topic {} for missing messages between sequence {} and {}",
                topicContext.getTopicId(), last.getSequenceNumber(), current.getSequenceNumber());

        return topicMessageRepository.findByFilter(newFilter)
                .filter(t -> t.getConsensusTimestamp().isBefore(current.getConsensusTimestamp()))
                .concatWith(Mono.just(current));
    }

    private enum Mode {
        QUERY,
        LISTEN;

        Mode next() {
            return this == QUERY ? LISTEN : this;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    @Data
    private class TopicContext {

        private final TopicMessageFilter filter;
        private final String topicId;
        private final Stopwatch stopwatch;
        private final AtomicLong count;
        private volatile TopicMessage lastTopicMessage;
        private volatile Mode mode;

        public TopicContext(TopicMessageFilter filter) {
            this.filter = filter;
            topicId = filter.getRealmNum() + "." + filter.getTopicNum();
            stopwatch = Stopwatch.createStarted();
            count = new AtomicLong(0L);
            mode = Mode.QUERY;
        }

        void onNext(TopicMessage topicMessage) {
            lastTopicMessage = topicMessage;
            count.incrementAndGet();
            log.trace("Topic {} received message #{}: {}", topicId, count, topicMessage);
        }

        boolean isNext(TopicMessage topicMessage) {
            return lastTopicMessage == null || topicMessage.getSequenceNumber() == lastTopicMessage
                    .getSequenceNumber() + 1;
        }

        void onComplete() {
            log.info("Topic {} {} complete with {} messages in {}", topicId, mode, count, stopwatch);
            mode = mode.next();
        }
    }
}
