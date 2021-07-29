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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;

class RecordFileRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private RecordFileRepository recordFileRepository;

    private long count = 0;

    @Test
    void findLatest() {
        RecordFile recordFile1 = recordFile();
        RecordFile recordFile2 = recordFile();
        RecordFile recordFile3 = recordFile();
        recordFileRepository.saveAll(List.of(recordFile1, recordFile2, recordFile3));
        assertThat(recordFileRepository.findLatest()).get().isEqualTo(recordFile3);
    }

    private RecordFile recordFile() {
        long id = ++count;
        return RecordFile.builder()
                .consensusStart(id)
                .consensusEnd(id)
                .count(id)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash("fileHash" + id)
                .hash("hash" + id)
                .idx(id)
                .loadEnd(id)
                .loadStart(id)
                .name(id + ".rcd")
                .nodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT))
                .previousHash("previousHash" + (id - 1))
                .version(1)
                .build();
    }
}
