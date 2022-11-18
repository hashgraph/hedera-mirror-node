package com.hedera.mirror.web3.repository;

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

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;

class RecordFileRepositoryTest extends Web3IntegrationTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Resource
    private RecordFileRepository recordFileRepository;

    @Test
    void findLatestIndex() {
        RecordFile recordFile1 = recordFile();
        recordFileRepository.save(recordFile1);

        RecordFile recordFile2 = recordFile();
        recordFileRepository.save(recordFile2);

        assertThat(recordFileRepository.findLatestIndex()).get().isEqualTo(recordFile2.getIndex());
    }

    @Test
    void findFileHashByIndex() {
        final var file = recordFile();
        recordFileRepository.save(file);

        assertThat(recordFileRepository.findHashByIndex(file.getIndex())).get().isEqualTo(file.getHash());
    }

    @Test
    void findLatestFile() {
        RecordFile recordFile1 = recordFile();
        recordFileRepository.save(recordFile1);

        RecordFile recordFile2 = recordFile();
        recordFileRepository.save(recordFile2);

        assertThat(recordFileRepository.findLatest()).get().isEqualTo(recordFile2);
    }

    private RecordFile recordFile() {
        return domainBuilder.recordFile().get();
    }
}
