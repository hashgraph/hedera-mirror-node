package com.hedera.mirror.monitor.subscribe.grpc;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;

import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessage;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.monitor.AbstractScenario;
import com.hedera.mirror.monitor.ScenarioProtocol;

class GrpcSubscription extends AbstractScenario<GrpcSubscriberProperties, TopicMessage> {

    GrpcSubscription(int id, GrpcSubscriberProperties properties) {
        super(id, properties);
    }

    @Override
    public ScenarioProtocol getProtocol() {
        return ScenarioProtocol.GRPC;
    }

    TopicMessageQuery getTopicMessageQuery() {
        long limit = properties.getLimit();
        Instant startTime = getLast().map(t -> t.consensusTimestamp.plusNanos(1))
                .orElseGet(properties::getStartTime);

        TopicMessageQuery topicMessageQuery = new TopicMessageQuery();
        topicMessageQuery.setEndTime(properties.getEndTime());
        topicMessageQuery.setLimit(limit > 0 ? limit - counter.get() : 0);
        topicMessageQuery.setStartTime(startTime);
        topicMessageQuery.setTopicId(TopicId.fromString(properties.getTopicId()));
        return topicMessageQuery;
    }

    @Override
    public void onNext(TopicMessage topicResponse) {
        log.trace("{}: Received message #{} with timestamp {}", this, topicResponse.sequenceNumber,
                topicResponse.consensusTimestamp);

        getLast().ifPresent(topicMessage -> {
            long expected = topicMessage.sequenceNumber + 1;
            if (topicResponse.sequenceNumber != expected) {
                log.warn("{}: Expected sequence number {} but received {}", this, expected,
                        topicResponse.sequenceNumber);
            }
        });

        super.onNext(topicResponse);
    }

    @Override
    public void onError(Throwable t) {
        Status.Code statusCode = Status.Code.UNKNOWN;
        if (t instanceof StatusRuntimeException) {
            statusCode = ((StatusRuntimeException) t).getStatus().getCode();
        }
        errors.add(statusCode.name());
    }

    @Override
    public String toString() {
        String name = getName();
        return getProperties().getSubscribers() <= 1 ? name : name + " #" + getId();
    }
}
