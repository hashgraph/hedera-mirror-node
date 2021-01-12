package com.hedera.mirror.importer.repository;

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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;

public class RecordFileRepositoryTest extends AbstractRepositoryTest {

    private RecordFile recordFile;

    @BeforeEach
    void setUp() {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        recordFile = RecordFile.builder()
                .consensusStart(0L)
                .consensusEnd(0L)
                .count(0L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash("fileHash")
                .hash("hash")
                .name("fileName")
                .nodeAccountId(nodeAccountId)
                .previousHash("previousHash")
                .version(1)
                .build();
    }

    @Test
    void insert() {
        recordFile = recordFileRepository.save(recordFile);
        Assertions.assertThat(recordFileRepository.findById(recordFile.getId()).get())
                .isNotNull()
                .isEqualTo(recordFile);
    }
}
