package com.hedera.mirror.test.e2e.acceptance.client;

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

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessage;

@Data
@Log4j2
public class SubscriptionResponse {
    private SubscriptionHandle subscription;
    private List<MirrorHCSResponse> mirrorHCSResponses = new ArrayList<>();
    private Stopwatch elapsedTime;
    private Throwable responseError;

    public void handleConsensusTopicResponse(TopicMessage topicMessage) {
        mirrorHCSResponses.add(new SubscriptionResponse.MirrorHCSResponse(topicMessage, Instant.now()));
        String messageAsString = new String(topicMessage.contents, StandardCharsets.UTF_8);
        log.trace("Received message: " + messageAsString
                + " consensus timestamp: " + topicMessage.consensusTimestamp
                + " topic sequence number: " + topicMessage.sequenceNumber);
    }

    public void handleThrowable(Throwable err) {
        log.error("GRPC error on subscription : {}", err.getMessage());
        responseError = err;
    }

    public boolean errorEncountered() {
        return responseError != null;
    }

    public void validateReceivedMessages() throws Exception {
        int invalidMessages = 0;
        TopicMessage lastTopicMessage = null;
        for (MirrorHCSResponse mirrorHCSResponseResponse : mirrorHCSResponses) {
            TopicMessage topicMessage = mirrorHCSResponseResponse
                    .getTopicMessage();

            Instant publishInstant = Instant
                    .ofEpochMilli(Longs.fromByteArray(topicMessage.contents));

            long publishSeconds = publishInstant.getEpochSecond();
            long consensusSeconds = topicMessage.consensusTimestamp.getEpochSecond();
            long receiptSeconds = mirrorHCSResponseResponse.getReceivedInstant().getEpochSecond();
            long e2eSeconds = receiptSeconds - publishSeconds;
            long consensusToDelivery = receiptSeconds - consensusSeconds;
            log.trace("Observed message, e2eSeconds: {}s, consensusToDelivery: {}s, publish timestamp: {}, " +
                            "consensus timestamp: {}, receipt time: {}, topic sequence number: {}",
                    e2eSeconds, consensusToDelivery, publishInstant, topicMessage.consensusTimestamp,
                    mirrorHCSResponseResponse.getReceivedInstant(), topicMessage.sequenceNumber);

            if (!validateResponse(lastTopicMessage, topicMessage)) {
                invalidMessages++;
            }

            lastTopicMessage = topicMessage;
        }

        if (invalidMessages > 0) {
            throw new Exception("Retrieved {} invalid messages in response");
        }

        log.info("{} messages were successfully validated", mirrorHCSResponses.size());
    }

    public boolean validateResponse(TopicMessage previousTopicMessage,
                                    TopicMessage currentTopicMessage) {
        boolean validResponse = true;

        if (previousTopicMessage != null && currentTopicMessage != null) {
            if (previousTopicMessage.consensusTimestamp.isAfter(currentTopicMessage.consensusTimestamp)) {
                log.error("Previous message {}, has a timestamp greater than current message {}",
                        previousTopicMessage.consensusTimestamp, currentTopicMessage.consensusTimestamp);
                validResponse = false;
            }

            if (previousTopicMessage.sequenceNumber + 1 != currentTopicMessage.sequenceNumber) {
                log.error("Previous message {}, has a sequenceNumber greater than current message {}",
                        previousTopicMessage.sequenceNumber, currentTopicMessage.sequenceNumber);
                validResponse = false;
            }
        }

        return validResponse;
    }

    @Data
    public static class MirrorHCSResponse {
        private final TopicMessage topicMessage;
        private final Instant receivedInstant;
    }
}
