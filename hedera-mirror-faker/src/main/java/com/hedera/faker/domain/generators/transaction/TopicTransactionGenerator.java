package com.hedera.faker.domain.generators.transaction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.faker.common.EntityManager;
import com.hedera.faker.common.TopicTransactionProperties;
import com.hedera.faker.common.TransactionGenerator;
import com.hedera.faker.domain.writer.DomainWriter;
import com.hedera.faker.sampling.Distribution;
import com.hedera.faker.sampling.FrequencyDistribution;
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
    private Map<Long, Integer> topicToNextSequenceNumber;

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
        runningHash = new byte[32]; // Size = 32bytes. TODO: what's correct size here?
        Arrays.fill(runningHash, (byte) 0x01);
        topicToNextSequenceNumber = new HashMap<>();
    }

    @Override
    protected void seedEntity(Transaction transaction) {
        createTopic(transaction);
    }

    private void createTopic(Transaction transaction) {
        transaction.setType(24);  // 24 = CONSENSUSCREATETOPIC
        Long newTopicId = entityManager.getTopics().newEntity();
        transaction.setEntityId(newTopicId);
        topicToNextSequenceNumber.put(newTopicId, 0);
        log.trace("CONSENSUSCREATETOPIC transaction: topicId {}", newTopicId);
    }

    private void deleteTopic(Transaction transaction) {
        transaction.setType(26);  // 26 = CONSENSUSDELETETOPIC
        Long topicId = entityManager.getTopics().getRandom();
        entityManager.getTopics().delete(topicId);
        transaction.setEntityId(topicId);
        log.trace("CONSENSUSDELETETOPIC transaction: topicId {}", topicId);
    }

    private void updateTopic(Transaction transaction) {
        transaction.setType(25);  // 25 = CONSENSUSUPDATETOPIC
        Long topicId = entityManager.getTopics().getRandom();
        transaction.setEntityId(topicId);
        log.trace("CONSENSUSUPDATETOPIC transaction: topicId {}", topicId);
    }

    private void submitMessage(Transaction transaction) {
        transaction.setType(27);  // 27 = CONSENSUSSUBMITMESSAGE
        Long topicId = entityManager.getTopics().getRandom();
        transaction.setEntityId(topicId);
        createTopicMessage(transaction.getConsensusNs(), topicId);
        log.trace("CONSENSUSSUBMITMESSAGE transaction: topicId {}", topicId);
    }

    private void createTopicMessage(long consensusNs, long topicId) {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusNs);
        topicMessage.setRealmNum(0);
        topicMessage.setTopicNum((int) topicId);
        topicMessage.setRunningHash(runningHash);
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
