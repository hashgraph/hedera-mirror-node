package com.hedera.mirror.grpc.jmeter.sampler;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.grpc.jmeter.ConnectionHandler;

@Log4j2
public class TopicMessageGeneratorSampler {
    private final ConnectionHandler connectionHandler;

    public TopicMessageGeneratorSampler(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    /**
     * Performs db operations necessary to simulate population of topic_message table Inserts messages into table for
     * given topic message for historic messages Continuously inserts messages into table to simulate incoming messages
     * Removes messages from table to restore original state of table for each test
     *
     * @param topicNum               topic num to perform operations on
     * @param historicalMessageCount the expected number of historic messages present
     * @param futureMessageCount     the minimum number of incoming messages to wait on
     * @param newTopicsMessageDelay  the period of time to wait between inserting messages
     * @param delSeqFrom             the sequence num from messages should be removed from topic
     * @return Success flag
     */
    public String populateTopicMessages(long topicNum, int historicalMessageCount, int futureMessageCount,
                                        long newTopicsMessageDelay, long delSeqFrom) throws InterruptedException {
        log.info("Running TopicMessageGenerator Sampler populateTopicMessages topicNum : {}, " +
                        "historicalMessageCount :" +
                        " {}, futureMessageCount : {}, " +
                        "newTopicsMessageDelay : {}, delSeqFrom : {}",
                topicNum, historicalMessageCount, futureMessageCount, newTopicsMessageDelay,
                delSeqFrom);

        topicNum = topicNum == -1 ? connectionHandler.getNextAvailableTopicID() : topicNum;

        populateHistoricalMessages(topicNum, historicalMessageCount);

        generateIncomingMessages(topicNum, futureMessageCount, newTopicsMessageDelay);

        connectionHandler.clearTopicMessages(topicNum, delSeqFrom);

        return "Success";
    }

    private void populateHistoricalMessages(long topicNum, int historicalMessageCount) {
        if (historicalMessageCount > 0) {
            Instant pastInstant = Instant.now().minus(7, ChronoUnit.DAYS);
            connectionHandler
                    .insertTopicMessage(historicalMessageCount, topicNum, pastInstant, -1);
        }
    }

    private void generateIncomingMessages(long topicNum, int futureMessageCount, long delay) throws InterruptedException {
        if (futureMessageCount > 0) {
            Instant start = Instant.now();
            int maxRunSeconds = 60;
            Instant incomingInstant;
            if (delay == 0) {
                incomingInstant = Instant.now();
                connectionHandler
                        .insertTopicMessage(futureMessageCount, topicNum, incomingInstant, -1);
            } else {
                while (true) {
                    incomingInstant = Instant.now();

                    if (incomingInstant.isAfter(start.plusSeconds(maxRunSeconds))) {
                        log.warn("Breaking out of loop. We don't want long living threads beyond {} seconds.",
                                maxRunSeconds);
                        break;
                    }

                    connectionHandler
                            .insertTopicMessage(futureMessageCount, topicNum, incomingInstant, -1);
                    Thread.sleep(delay);
                }
            }
        }
    }
}
