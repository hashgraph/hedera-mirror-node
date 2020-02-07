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

import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.grpc.jmeter.ConnectionHandler;
import com.hedera.mirror.grpc.jmeter.props.MessageGenerator;

@Log4j2
@RequiredArgsConstructor
public class TopicMessageGeneratorSampler {

    public static final Instant INCOMING_START = Instant.now();
    private final ConnectionHandler connectionHandler;

    /**
     * Performs db operations necessary to simulate population of topic_message table Inserts messages into table for
     * given topic message for historic messages Continuously inserts messages into table to simulate incoming messages
     * Removes messages from table to restore original state of table for each test
     *
     * @param messageGen messageGen object containing properties for message generation
     * @return Success flag
     */
    public void populateTopicMessages(MessageGenerator messageGen) throws InterruptedException {
        log.info("Populating topic messages: {}", messageGen);

        long topicNum = messageGen.getTopicNum();
        connectionHandler.clearTopicMessages(topicNum, messageGen.getDeleteFromSequence());
        topicNum = topicNum == -1 ? connectionHandler.getNextAvailableTopicID() : topicNum;

        populateHistoricalMessages(messageGen);
        generateIncomingMessages(messageGen);

        connectionHandler.close();
        log.debug("Successfully populated topic messages");
    }

    private void populateHistoricalMessages(MessageGenerator messageGenerator) {
        if (messageGenerator.getHistoricMessagesCount() > 0) {
            Instant pastInstant = Instant.EPOCH.plus(7, ChronoUnit.DAYS);
            connectionHandler.insertTopicMessage(messageGenerator.getHistoricMessagesCount(), messageGenerator
                    .getTopicNum(), pastInstant, -1);
        }
    }

    private void generateIncomingMessages(MessageGenerator messageGenerator) {
        if (messageGenerator.getFutureMessagesCount() > 0) {
            // ensure all incoming messages occur after INCOMING_START as sampler uses said time to distinguish
            long cycleCount = 0;

            if (messageGenerator.getNewTopicsMessageDelay() <= 0) {
                connectionHandler.insertTopicMessage(messageGenerator.getFutureMessagesCount(), messageGenerator
                        .getTopicNum(), INCOMING_START, -1);
            } else {
                while (cycleCount < messageGenerator.getTopicMessageEmitCycles()) {
                    connectionHandler.insertTopicMessage(messageGenerator.getFutureMessagesCount(), messageGenerator
                            .getTopicNum(), INCOMING_START, -1);
                    ++cycleCount;
                    Uninterruptibles
                            .sleepUninterruptibly(messageGenerator.getNewTopicsMessageDelay(), TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
