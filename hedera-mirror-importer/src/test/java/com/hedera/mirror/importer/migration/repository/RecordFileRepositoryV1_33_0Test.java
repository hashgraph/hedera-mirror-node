package com.hedera.mirror.importer.migration.repository;

/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

import javax.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.migration.domain.RecordFileV1_33_0;
import com.hedera.mirror.importer.repository.AbstractRepositoryTest;

@Tag("failincci")
@TestPropertySource(properties = "spring.flyway.target=1.33.0")
class RecordFileRepositoryV1_33_0Test extends AbstractRepositoryTest {

    @Resource
    protected RecordFileRepositoryV1_33_0 recordFileRepositoryV1_33_0;

    private RecordFileV1_33_0 recordFile;

    @BeforeEach
    void setUp() {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        recordFile = RecordFileV1_33_0.builder()
                .consensusStart(0L)
                .consensusEnd(0L)
                .count(0L)
                .fileHash("fileHash")
                .name("fileName")
                .nodeAccountId(nodeAccountId)
                .previousHash("previousHash")
                .build();
    }

    @Test
    void insert() {
        recordFile = recordFileRepositoryV1_33_0.save(recordFile);
        Assertions.assertThat(recordFileRepositoryV1_33_0.findById(recordFile.getId()).get())
                .isNotNull()
                .isEqualTo(recordFile);
    }
}
