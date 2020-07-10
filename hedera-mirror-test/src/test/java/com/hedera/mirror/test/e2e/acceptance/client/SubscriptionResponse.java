package com.hedera.mirror.test.e2e.acceptance.client;

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

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Longs;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;

import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;

@Data
@Log4j2
public class SubscriptionResponse {
    private MirrorSubscriptionHandle subscription;
    private List<MirrorHCSResponse> messages = new ArrayList<>();
    private Stopwatch elapsedTime;
    private Throwable responseError;

    public void handleMirrorConsensusTopicResponse(MirrorConsensusTopicResponse topicResponse) {
        messages.add(new SubscriptionResponse.MirrorHCSResponse(topicResponse, Instant.now()));
        String messageAsString = new String(topicResponse.message, StandardCharsets.UTF_8);
        log.trace("Received message: " + messageAsString
                + " consensus timestamp: " + topicResponse.consensusTimestamp
                + " topic sequence number: " + topicResponse.sequenceNumber);
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
        MirrorConsensusTopicResponse lastMirrorConsensusTopicResponse = null;
        for (MirrorHCSResponse mirrorHCSResponseResponse : messages) {
            MirrorConsensusTopicResponse mirrorConsensusTopicResponse = mirrorHCSResponseResponse
                    .getMirrorConsensusTopicResponse();

            Instant publishInstant = Instant
                    .ofEpochMilli(Longs.fromByteArray(Base64.decodeBase64(mirrorConsensusTopicResponse.message)));

            long publishSeconds = publishInstant.getEpochSecond();
            long consensusSeconds = mirrorConsensusTopicResponse.consensusTimestamp.getEpochSecond();
            long receiptSeconds = mirrorHCSResponseResponse.getReceivedInstant().getEpochSecond();
            long e2eSeconds = receiptSeconds - publishSeconds;
            long consensusToDelivery = receiptSeconds - consensusSeconds;
            log.trace("Observed message, e2eSeconds: {}s, consensusToDelivery: {}s, publish timestamp: {}, " +
                            "consensus timestamp: {}, receipt time: {}, topic sequence number: {}",
                    e2eSeconds, consensusToDelivery, publishInstant, mirrorConsensusTopicResponse.consensusTimestamp,
                    mirrorHCSResponseResponse.getReceivedInstant(), mirrorConsensusTopicResponse.sequenceNumber);

            if (!validateResponse(lastMirrorConsensusTopicResponse, mirrorConsensusTopicResponse)) {
                invalidMessages++;
            }

            lastMirrorConsensusTopicResponse = mirrorConsensusTopicResponse;
        }

        if (invalidMessages > 0) {
            throw new Exception("Retrieved {} invalid messages in response");
        }

        log.info("{} messages were successfully validated", messages.size());
    }

    public boolean validateResponse(MirrorConsensusTopicResponse previousResponse,
                                    MirrorConsensusTopicResponse currentResponse) {
        boolean validResponse = true;

        if (previousResponse != null && currentResponse != null) {
            if (previousResponse.consensusTimestamp.isAfter(currentResponse.consensusTimestamp)) {
                log.error("Previous message {}, has a timestamp greater than current message {}",
                        previousResponse.consensusTimestamp, currentResponse.consensusTimestamp);
                validResponse = false;
            }

            if (previousResponse.sequenceNumber + 1 != currentResponse.sequenceNumber) {
                log.error("Previous message {}, has a sequenceNumber greater than current message {}",
                        previousResponse.sequenceNumber, currentResponse.sequenceNumber);
                validResponse = false;
            }
        }

        return validResponse;
    }

    @Data
    public static class MirrorHCSResponse {
        private final MirrorConsensusTopicResponse mirrorConsensusTopicResponse;
        private final Instant receivedInstant;
    }
}
