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
import java.util.concurrent.CountDownLatch;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.grpc.jmeter.ConnectionHandler;

@Log4j2
public class TopicMessageGeneratorSampler {
    private final ConnectionHandler connectionHandler;
    private final int threadNum;

    public TopicMessageGeneratorSampler(ConnectionHandler connectionHndler, int thrdNum) {
        connectionHandler = connectionHndler;
        threadNum = thrdNum;
    }

    public String populateTopicMessages(long topicNum, int historicalMessageCount, int futureMessageCount,
                                        long newTopicsMessageDelay, long delSeqFrom) throws InterruptedException {
        log.info("THRD {} : Running TopicMessageGenerator Sampler populateTopicMessages topicNum : {}, " +
                        "historicalMessageCount :" +
                        " {}, futureMessageCount : {}, " +
                        "newTopicsMessageDelay : {}, delSeqFrom : {}",
                threadNum, topicNum, historicalMessageCount, futureMessageCount, newTopicsMessageDelay,
                delSeqFrom);

        CountDownLatch historicMessagesLatch = new CountDownLatch(historicalMessageCount);
        CountDownLatch incomingMessagesLatch = new CountDownLatch(futureMessageCount);

        topicNum = topicNum == -1 ? connectionHandler.getNextAvailableTopicID() : topicNum;

        populateHistoricalMessages(topicNum, historicalMessageCount, historicMessagesLatch);

        generateIncomingMessages(topicNum, futureMessageCount, incomingMessagesLatch, newTopicsMessageDelay);

        deleteMessagesFromTopic(topicNum, delSeqFrom);

        return "Success";
    }

    private void populateHistoricalMessages(long topicNum, int historicalMessageCount,
                                            CountDownLatch historicMessagesLatch) {
        if (historicalMessageCount > 0) {
            Instant pastInstant = Instant.now().minus(7, ChronoUnit.DAYS);
            connectionHandler
                    .InsertTopicMessage(historicalMessageCount, topicNum, pastInstant, historicMessagesLatch, -1);
        }
    }

    private void generateIncomingMessages(long topicNum, int futureMessageCount, CountDownLatch incomingMessagesLatch
            , long delay) throws InterruptedException {
        if (futureMessageCount > 0) {
            Instant start = Instant.now();
            int maxRunSeconds = 60;
            Instant incomingInstant;
            if (delay == 0) {
                incomingInstant = Instant.now();
                connectionHandler
                        .InsertTopicMessage(futureMessageCount, topicNum, incomingInstant, incomingMessagesLatch, -1);
            } else {
                while (true) {
                    incomingInstant = Instant.now();

                    if (incomingInstant.isAfter(start.plusSeconds(maxRunSeconds))) {
                        log.warn("Breaking out of loop. We don't want long living threads beyond {} seconds.",
                                maxRunSeconds);
                        break;
                    }

                    connectionHandler
                            .InsertTopicMessage(futureMessageCount, topicNum, incomingInstant, incomingMessagesLatch,
                                    -1);
                    Thread.sleep(delay);
                }
            }
        }
    }

    private void deleteMessagesFromTopic(long topicId, long seqNumFrom) {
        if (topicId >= 0 && seqNumFrom >= 0) {
            connectionHandler.clearTopicMessages(topicId, seqNumFrom);
        }
    }
}
