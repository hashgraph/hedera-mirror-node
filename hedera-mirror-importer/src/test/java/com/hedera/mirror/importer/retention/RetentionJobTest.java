package com.hedera.mirror.importer.retention;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class RetentionJobTest extends IntegrationTest {

    private final RecordFileRepository recordFileRepository;
    private final RetentionJob retentionJob;
    private final RetentionProperties retentionProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        retentionProperties.setEnabled(true);
    }

    @AfterEach
    void cleanup() {
        retentionProperties.setEnabled(false);
    }

    @Test
    void disabled() {
        // given
        retentionProperties.setEnabled(false);
        retentionProperties.setPeriod(Duration.ofDays(-1L));
        var recordFile = domainBuilder.recordFile().persist();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
    }

    @Test
    void noData() {
        // given
        retentionProperties.setPeriod(Duration.ofDays(-1L));
        var transaction = domainBuilder.transaction().persist();

        // when
        retentionJob.prune();

        // then
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
    }

    @Test
    void startAndEndEqualsSame() {
        // given
        retentionProperties.setPeriod(Duration.ofDays(-1L));
        long timestamp = 1L;
        domainBuilder.recordFile().customize(r -> r.consensusEnd(timestamp).consensusStart(timestamp)).persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).persist();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void nothingToPrune() {
        // given
        retentionProperties.setBatchPeriod(Duration.ofNanos(2L));
        retentionProperties.setPeriod(Duration.ofDays(30L));
        var recordFile1 = domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(recordFile1.getConsensusStart())).persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(recordFile2.getConsensusStart())).persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(recordFile3.getConsensusStart())).persist();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.count()).isEqualTo(3);
        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void prune() {
        // given
        retentionProperties.setBatchPeriod(Duration.ofNanos(2L));
        retentionProperties.setPeriod(Duration.ofNanos(2L));
        var recordFile1 = domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(recordFile1.getConsensusStart())).persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(recordFile2.getConsensusStart())).persist();
        var transaction3 = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(recordFile3.getConsensusStart())).persist();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile3);
        assertThat(transactionRepository.findAll()).containsExactly(transaction3);
    }
}
