package com.hedera.datagenerator.domain;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.hedera.datagenerator.DataGeneratorProperties;
import com.hedera.datagenerator.common.EntityManager;
import com.hedera.datagenerator.domain.generators.entity.EntityGenerator;
import com.hedera.datagenerator.domain.generators.transaction.DomainTransactionGenerator;
import com.hedera.datagenerator.domain.writer.DomainWriter;
import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.RandomDistributionFromRange;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;
import com.hedera.mirror.importer.util.Utility;

@Named
@Log4j2
public class DomainDriver implements ApplicationRunner {
    private final DataGeneratorProperties properties;
    private final DomainTransactionGenerator domainTransactionGenerator;
    private final EntityManager entityManager;
    private final DomainWriter domainWriter;
    private final SqlEntityListener sqlEntityListener;

    /**
     * Generates nanos part of the timestamp
     */
    private final Distribution<Long> consensusNanoAdjustmentsDistribution;

    public DomainDriver(DataGeneratorProperties properties, DomainTransactionGenerator domainTransactionGenerator,
                        EntityManager entityManager, DomainWriter domainWriter,
                        SqlEntityListener sqlEntityListener) {
        this.properties = properties;
        this.domainTransactionGenerator = domainTransactionGenerator;
        this.entityManager = entityManager;
        this.domainWriter = domainWriter;
        this.sqlEntityListener = sqlEntityListener;
        sqlEntityListener.onStart(new StreamFileData("", null));
        consensusNanoAdjustmentsDistribution = new RandomDistributionFromRange(0, 1000000000);
    }

    /**
     * Top level runner for generating test data. Iterates from start time (from configuration) to end time and
     * generates transactions for intermediate seconds based on transactions-per-second configuration.
     */
    @Override
    public void run(ApplicationArguments args) {
        long currentSimulationTime = properties.getStartTimeSec();
        long totalDurationSec = properties.getTotalDuration().toSeconds();
        long endTime = currentSimulationTime + totalDurationSec;
        log.info("Simulation time from {} to {} (time period: {}sec)", currentSimulationTime, endTime,
                totalDurationSec);
        int numTransactionsGenerated = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        EntityId nodeAccountId = EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT);
        // Iterate from start time to end time.
        while (currentSimulationTime < endTime) {
            int numTransactions = properties.getTransactionsPerSecond().sample().intValue();
            log.debug("Generating {} transactions for time {}", numTransactions, currentSimulationTime);
            List<Long> consensusNanoAdjustments = consensusNanoAdjustmentsDistribution.sampleDistinct(numTransactions);
            Collections.sort(consensusNanoAdjustments);
            // Generate transactions, one for each sampled nano adjustment (within the "current" simulated second)
            for (long nanoAdjustment : consensusNanoAdjustments) {
                long consensusTimestampNs = Utility.convertToNanos(currentSimulationTime, nanoAdjustment);
                domainTransactionGenerator.generateTransaction(consensusTimestampNs);
                numTransactionsGenerated++;
                if (numTransactionsGenerated % 10000 == 0) {
                    log.info("Generated {} transactions in {}", numTransactionsGenerated, stopwatch);
                }
            }
            if (currentSimulationTime % properties.getBalancesFileDuration().getSeconds() == 0) {
                long lastConsensusNanoAdjustment = consensusNanoAdjustments.get(consensusNanoAdjustments.size() - 1);
                writeBalances(Utility.convertToNanos(currentSimulationTime, lastConsensusNanoAdjustment));
            }
            currentSimulationTime++;
        }
        log.info("Generated {} transactions in {}", numTransactionsGenerated, stopwatch);
        new EntityGenerator().generateAndWriteEntities(entityManager, domainWriter);
        RecordFile recordFile = RecordFile.builder()
                .consensusStart(0L)
                .consensusEnd(1L)
                .count(0L)
                .id(1L)
                .name("")
                .loadStart(0L)
                .loadEnd(1L)
                .hash("")
                .previousHash("")
                .nodeAccountId(nodeAccountId)
                .version(2)
                .build();
        log.info("Writing data to db");
        sqlEntityListener.onEnd(recordFile); // writes data to db
        domainWriter.flush(); // writes data to db
        log.info("Total time taken: {}", stopwatch);
    }

    // Writes account balances stream
    private void writeBalances(long consensusNs) {
        for (Map.Entry<EntityId, Long> entry : entityManager.getBalances().entrySet()) {
            var entity = entry.getKey();
            domainWriter.onAccountBalance(new AccountBalance(consensusNs, entity, entry.getValue()));
        }
        log.debug("Wrote balances data at {}", consensusNs);
    }
}
