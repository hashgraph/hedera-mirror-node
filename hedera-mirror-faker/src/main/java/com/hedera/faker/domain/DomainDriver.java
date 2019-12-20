package com.hedera.faker.domain;
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

import com.google.common.base.Stopwatch;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.hedera.faker.FakerProperties;
import com.hedera.faker.common.EntityManager;
import com.hedera.faker.domain.generators.entity.EntityGenerator;
import com.hedera.faker.domain.generators.transaction.DomainTransactionGenerator;
import com.hedera.faker.domain.writer.DomainWriter;
import com.hedera.faker.sampling.Distribution;
import com.hedera.faker.sampling.RandomDistributionFromRange;
import com.hedera.mirror.importer.util.Utility;

@Named
@Log4j2
public class DomainDriver implements ApplicationRunner {
    private final FakerProperties properties;
    private final DomainTransactionGenerator domainTransactionGenerator;
    private final EntityManager entityManager;
    private final DomainWriter domainWriter;

    /**
     * Generates nanos part of the timestamp
     */
    private final Distribution<Long> consensusNanoAdjustmentsDistribution;

    public DomainDriver(FakerProperties properties, DomainTransactionGenerator domainTransactionGenerator,
                        EntityManager entityManager, DomainWriter domainWriter) {
        this.properties = properties;
        this.domainTransactionGenerator = domainTransactionGenerator;
        this.entityManager = entityManager;
        this.domainWriter = domainWriter;
        consensusNanoAdjustmentsDistribution = new RandomDistributionFromRange(0, 1000000000);
    }

    /**
     * Top level runner for generating fake data. Iterates from start time (from configuration) to end time and
     * generates fake transactions for intermediate seconds based on transactions-per-second configuration.
     */
    @Override
    public void run(ApplicationArguments args) {
        long currentFakeTime = properties.getStartTimeSec();
        long totalDurationSec = properties.getTotalDuration().toSeconds();
        long endTime = currentFakeTime + totalDurationSec;
        log.info("Simulation time from {} to {} (time period: {}sec)", currentFakeTime, endTime, totalDurationSec);
        int numTransactionsGenerated = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        // Iterate from start time to end time.
        while (currentFakeTime < endTime) {
            int numTransactions = properties.getTransactionsPerSecond().sample().intValue();
            log.debug("Generating {} transactions for time {}", numTransactions, currentFakeTime);
            List<Long> consensusNanoAdjustments = consensusNanoAdjustmentsDistribution.sampleDistinct(numTransactions);
            Collections.sort(consensusNanoAdjustments);
            // Generate transactions, one for each sampled nano adjustment (within the "current" simulated second)
            for (long nanoAdjustment : consensusNanoAdjustments) {
                long consensusTimestampNs = Utility.convertToNanos(currentFakeTime, nanoAdjustment);
                domainTransactionGenerator.generateTransaction(consensusTimestampNs);
                numTransactionsGenerated++;
                if (numTransactionsGenerated % 10000 == 0) {
                    log.info("Generated {} transactions in {}", numTransactionsGenerated, stopwatch);
                }
            }
            if (currentFakeTime % properties.getBalancesFileDuration().getSeconds() == 0) {
                long lastConsensusNanoAdjustment = consensusNanoAdjustments.get(consensusNanoAdjustments.size() - 1);
                writeBalances(Utility.convertToNanos(currentFakeTime, lastConsensusNanoAdjustment));
            }
            currentFakeTime++;
        }
        log.info("Generated {} transactions in {}", numTransactionsGenerated, stopwatch);
        new EntityGenerator().generateAndWriteEntities(entityManager, domainWriter);
        log.info("Total time taken: {}", stopwatch);
    }

    // Writes account balances stream
    private void writeBalances(long consensusNs) {
        for (Map.Entry<Long, Long> entry : entityManager.getBalances().entrySet()) {
            domainWriter.addAccountBalances(consensusNs, entry.getValue(), entry.getKey());
        }
        log.debug("Wrote balances data at {}", consensusNs);
    }
}
