package com.hedera.mirror.importer.repository;

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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

class RecordFileRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private RecordFileRepository recordFileRepository;

    @Test
    void findLatest() {
        domainBuilder.recordFile().persist();
        domainBuilder.recordFile().persist();
        var expected = domainBuilder.recordFile().persist();
        assertThat(recordFileRepository.findLatest()).get().isEqualTo(expected);
    }

    @Test
    void findLatestMissingGasUsedBefore() {
        var recordFile1 = domainBuilder.recordFile().customize(r -> r.gasUsed(-1)).persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().customize(r -> r.gasUsed(-1)).persist();
        var recordFile4 = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(recordFile4.getConsensusEnd() + 1L)).get()
                .isEqualTo(recordFile3);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(recordFile3.getConsensusEnd())).get()
                .isEqualTo(recordFile1);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(recordFile2.getConsensusEnd())).get()
                .isEqualTo(recordFile1);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(recordFile1.getConsensusEnd())).isEmpty();
    }

    @Test
    void findLatestMissingGasUsedBeforeEmpty() {
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(100L)).isEmpty();
    }
}
