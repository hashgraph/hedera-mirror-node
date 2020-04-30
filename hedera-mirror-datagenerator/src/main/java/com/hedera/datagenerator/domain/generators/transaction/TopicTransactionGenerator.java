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
import com.hedera.datagenerator.domain.writer.DomainWriter;
import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;

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
    private final Map<Long, Integer> topicToNextSequenceNumber;

    public TopicTransactionGenerator(
            TopicTransactionProperties properties, EntityManager entityManager, DomainWriter domainWriter) {
        super(entityManager, domainWriter, properties.getNumSeedTopics());
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
        Entities newTopic = entityManager.getTopics().newEntity();
        transaction.setEntity(newTopic);
        topicToNextSequenceNumber.put(newTopic.getId(), 0);
        createTopicMessage(transaction.getConsensusNs(), newTopic.getId());
        log.trace("CONSENSUSCREATETOPIC transaction: topicId {}", newTopic);
    }

    private void deleteTopic(Transaction transaction) {
        transaction.setType(26);  // 26 = CONSENSUSDELETETOPIC
        Entities topic = entityManager.getTopics().getRandom();
        entityManager.getTopics().delete(topic);
        transaction.setEntity(topic);
        log.trace("CONSENSUSDELETETOPIC transaction: topicId {}", topic.getId());
    }

    private void updateTopic(Transaction transaction) {
        transaction.setType(25);  // 25 = CONSENSUSUPDATETOPIC
        Entities topic = entityManager.getTopics().getRandom();
        transaction.setEntity(topic);
        createTopicMessage(transaction.getConsensusNs(), topic.getId());
        log.trace("CONSENSUSUPDATETOPIC transaction: topicId {}", topic.getId());
    }

    private void submitMessage(Transaction transaction) {
        transaction.setType(27);  // 27 = CONSENSUSSUBMITMESSAGE
        Entities topic = entityManager.getTopics().getRandom();
        transaction.setEntity(topic);
        createTopicMessage(transaction.getConsensusNs(), topic.getId());
        log.trace("CONSENSUSSUBMITMESSAGE transaction: topicId {}", topic.getId());
    }

    private void createTopicMessage(long consensusNs, long topicId) {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusNs);
        topicMessage.setRealmNum(0);
        topicMessage.setTopicNum((int) topicId);
        topicMessage.setRunningHash(runningHash);
        topicMessage.setRunningHashVersion(2);
        int sequenceNumber = topicToNextSequenceNumber.get(topicId);
        topicMessage.setSequenceNumber(sequenceNumber);
        topicToNextSequenceNumber.put(topicId, sequenceNumber + 1);
        long messageSize = properties.getMessageSize().sample();
        byte[] messageBytes = new byte[(int) messageSize];
        new Random().nextBytes(messageBytes);
        topicMessage.setMessage(messageBytes);
        domainWriter.addTopicMessage(topicMessage);
    }
}
