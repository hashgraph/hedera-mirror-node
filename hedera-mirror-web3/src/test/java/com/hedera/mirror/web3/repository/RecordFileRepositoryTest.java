/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
        var earliest = domainBuilder.recordFile().persist();
        domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findEarliest()).get().isEqualTo(earliest);
    }

    @Test
    void findFileHashByIndex() {
        final var file = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findByIndex(file.getIndex()))
                .map(RecordFile::getHash)
                .hasValue(file.getHash());
    }

    @Test
    void findLatestFile() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findRecordFileByIndex() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();
        long blockNumber = latest.getIndex();

        assertThat(recordFileRepository.findByIndex(blockNumber))
                .map(RecordFile::getConsensusEnd)
                .hasValue(latest.getConsensusEnd());
    }

    @Test
    void findRecordFileByIndexNotExists() {
        long nonExistentBlockNumber = 1L;
        assertThat(recordFileRepository.findByIndex(nonExistentBlockNumber)).isEmpty();
    }
}
