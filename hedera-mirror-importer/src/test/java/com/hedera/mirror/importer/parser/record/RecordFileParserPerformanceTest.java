package com.hedera.mirror.importer.parser.record;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlProperties;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordFileParserPerformanceTest extends IntegrationTest {

    private final boolean defaultParallelIngestion = false;
    private List<RecordFile> recordFiles;
    @Value("classpath:data/recordstreams/performance/v5/*.rcd")
    private Resource[] testFiles;
    @Autowired
    private RecordFileParser recordFileParser;
    @Autowired
    private RecordFileReader recordFileReader;
    @Autowired
    private RecordFileRepository recordFileRepository;
    @Autowired
    private SqlProperties sqlProperties;

    @BeforeEach
    void setup() throws Exception {
        recordFiles = new ArrayList<>();
        EntityId nodeAccountId = EntityId.of(0L, 0L, 3L, EntityTypeEnum.ACCOUNT);
        for (int index = 0; index < testFiles.length; index++) {
            RecordFile recordFile = recordFileReader.read(StreamFileData.from(testFiles[index].getFile()));
            recordFile.setIndex((long) index);
            recordFile.setNodeAccountId(nodeAccountId);
            recordFiles.add(recordFile);
        }

        sqlProperties.setParallelIngestion(defaultParallelIngestion);
    }

    @Test
    @Timeout(15)
    void parseInSequence() {
        recordFiles.forEach(recordFileParser::parse);
        assertThat(recordFileRepository.count()).isEqualTo(recordFiles.size());
    }

    @Test
    @Timeout(15)
    void parseInParallel() {
        sqlProperties.setParallelIngestion(true);
        recordFiles.forEach(recordFileParser::parse);
        assertThat(recordFileRepository.count()).isEqualTo(recordFiles.size());
    }
}
