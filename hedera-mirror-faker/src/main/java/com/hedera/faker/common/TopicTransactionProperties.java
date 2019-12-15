package com.hedera.faker.common;

import javax.annotation.PostConstruct;
import javax.inject.Named;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.faker.sampling.NumberDistributionConfig;

@Data
@EqualsAndHashCode(callSuper = false)
@Named
@ConfigurationProperties("faker.transaction.topic")
public class TopicTransactionProperties {

    private final NumberDistributionConfig messageSize = new NumberDistributionConfig();

    /**
     * Relative frequency of CONSENSUSCREATETOPIC transactions
     */
    private int createsFrequency;

    /**
     * Relative frequency of CONSENSUSDELETETOPIC transactions
     */
    private int deletesFrequency;

    /**
     * Relative frequency of CONSENSUSUPDATETOPIC transactions
     */
    private int updatesFrequency;

    /**
     * Relative frequency of CONSENSUSSUBMITMESSAGE transactions
     */
    private int submitMessageFrequency;

    /**
     * When generating transactions, first 'numSeedTopics' number of transactions will be of type CONSENSUSCREATETOPIC
     * only. This is to seed the system with some topics for deletes/updates/submitMessage.
     */
    private int numSeedTopics;

    @PostConstruct
    void initDistributions() {
        messageSize.initDistribution();
    }
}
