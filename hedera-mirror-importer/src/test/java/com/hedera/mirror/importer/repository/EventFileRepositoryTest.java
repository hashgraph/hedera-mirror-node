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

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.EventFile;

class EventFileRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private EventFileRepository eventFileRepository;

    private long count = 0;

    @Test
    void findLatest() {
        EventFile eventFile1 = eventFile();
        EventFile eventFile2 = eventFile();
        EventFile eventFile3 = eventFile();
        eventFileRepository.saveAll(List.of(eventFile1, eventFile2, eventFile3));
        assertThat(eventFileRepository.findLatest()).get().isEqualTo(eventFile3);
    }

    private EventFile eventFile() {
        long id = ++count;
        EventFile eventFile = new EventFile();
        eventFile.setConsensusStart(id);
        eventFile.setConsensusEnd(id);
        eventFile.setCount(id);
        eventFile.setFileHash("fileHash" + id);
        eventFile.setLoadEnd(id);
        eventFile.setLoadStart(id);
        eventFile.setName(id + ".evt");
        eventFile.setNodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT));
        return eventFile;
    }
}
