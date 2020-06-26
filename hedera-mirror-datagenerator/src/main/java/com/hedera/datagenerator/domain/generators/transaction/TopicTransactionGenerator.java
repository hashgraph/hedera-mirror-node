package com.hedera.datagenerator.domain.generators.transaction;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.EntityManager;
import com.hedera.datagenerator.common.TopicTransactionProperties;
import com.hedera.datagenerator.common.TransactionGenerator;
import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

/**
 * Generates topic transactions (CONSENSUSCREATETOPIC, CONSENSUSUPDATETOPIC, CONSENSUSDELETETOPIC,
 * CONSENSUSSUBMITMESSAGE)
 */
@Log4j2
@Named
public class TopicTransactionGenerator extends TransactionGenerator {

    private final TopicTransactionProperties properties;
    @Getter
    private final Distribution<Consumer<Transaction>> transactionDistribution;
    private final byte[] runningHash;
    private final Map<EntityId, Integer> topicToNextSequenceNumber;

    public TopicTransactionGenerator(
            TopicTransactionProperties properties, EntityManager entityManager, EntityListener entityListener) {
        super(entityManager, entityListener, properties.getNumSeedTopics());
        this.properties = properties;
        transactionDistribution = new FrequencyDistribution<>(Map.of(
                this::createTopic, this.properties.getCreatesFrequency(),
                this::deleteTopic, this.properties.getDeletesFrequency(),
                this::updateTopic, this.properties.getUpdatesFrequency(),
                this::submitMessage, this.properties.getSubmitMessageFrequency()
        ));
        runningHash = new byte[48]; // Hashes are sha-384
        Arrays.fill(runningHash, (byte) 0x01);
        topicToNextSequenceNumber = new HashMap<>();
    }

    @Override
    protected void seedEntity(Transaction transaction) {
        createTopic(transaction);
    }

    private void createTopic(Transaction transaction) {
        transaction.setType(24);  // 24 = CONSENSUSCREATETOPIC
        EntityId newTopicNum = entityManager.getTopics().newEntity();
        transaction.setEntityId(newTopicNum);
        topicToNextSequenceNumber.put(newTopicNum, 0);
        createTopicMessage(transaction.getConsensusNs(), newTopicNum);
        log.trace("CONSENSUSCREATETOPIC transaction: topicId {}", newTopicNum);
    }

    private void deleteTopic(Transaction transaction) {
        transaction.setType(26);  // 26 = CONSENSUSDELETETOPIC
        EntityId topicNum = entityManager.getTopics().getRandomEntity();
        entityManager.getTopics().delete(topicNum);
        transaction.setEntityId(topicNum);
        log.trace("CONSENSUSDELETETOPIC transaction: topicId {}", topicNum);
    }

    private void updateTopic(Transaction transaction) {
        transaction.setType(25);  // 25 = CONSENSUSUPDATETOPIC
        EntityId topicNum = entityManager.getTopics().getRandomEntity();
        transaction.setEntityId(topicNum);
        createTopicMessage(transaction.getConsensusNs(), topicNum);
        log.trace("CONSENSUSUPDATETOPIC transaction: topicId {}", topicNum);
    }

    private void submitMessage(Transaction transaction) {
        transaction.setType(27);  // 27 = CONSENSUSSUBMITMESSAGE
        EntityId topicNum = entityManager.getTopics().getRandomEntity();
        transaction.setEntityId(topicNum);
        createTopicMessage(transaction.getConsensusNs(), topicNum);
        log.trace("CONSENSUSSUBMITMESSAGE transaction: topicId {}", topicNum);
    }

    private void createTopicMessage(long consensusNs, EntityId topic) {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusNs);
        topicMessage.setRealmNum(topic.getRealmNum().intValue());
        topicMessage.setTopicNum(topic.getEntityNum().intValue());
        topicMessage.setRunningHash(runningHash);
        topicMessage.setRunningHashVersion(2);
        int sequenceNumber = topicToNextSequenceNumber.get(topic);
        topicMessage.setSequenceNumber(sequenceNumber);
        topicToNextSequenceNumber.put(topic, sequenceNumber + 1);
        long messageSize = properties.getMessageSize().sample();
        byte[] messageBytes = new byte[(int) messageSize];
        new Random().nextBytes(messageBytes);
        topicMessage.setMessage(messageBytes);
        entityListener.onTopicMessage(topicMessage);
    }
}
