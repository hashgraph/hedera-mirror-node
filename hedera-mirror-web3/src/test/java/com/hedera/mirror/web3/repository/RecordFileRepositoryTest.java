/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class RecordFileRepositoryTest extends Web3IntegrationTest {

    @Resource
    private RecordFileRepository recordFileRepository;

    @Test
    void findLatestIndex() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatestIndex()).get().isEqualTo(latest.getIndex());
    }

    @Test
    void findEarliest() {
        final var genesisRecordFile =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findEarliest()).get().isEqualTo(genesisRecordFile);
    }

    @Test
    void findLatest() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findByIndex() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();
        long blockNumber = latest.getIndex();

        assertThat(recordFileRepository.findByIndex(blockNumber))
                .map(RecordFile::getConsensusEnd)
                .hasValue(latest.getConsensusEnd());
    }

    @Test
    void findByIndexNotExists() {
        long nonExistentBlockNumber = 1L;
        assertThat(recordFileRepository.findByIndex(nonExistentBlockNumber)).isEmpty();
    }

    @Test
    void findByIndexRange() {
        domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();
        domainBuilder.recordFile().persist();
        assertThat(recordFileRepository.findByIndexRange(recordFile2.getIndex(), recordFile3.getIndex()))
                .containsExactly(recordFile2, recordFile3);
    }

    @Test
    void findByTimestamp() {
        var timestamp = domainBuilder.timestamp();
        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(timestamp);
                    r.consensusEnd(timestamp + 1);
                })
                .persist();
        assertThat(recordFileRepository.findByTimestamp(timestamp)).contains(recordFile);
    }
}
