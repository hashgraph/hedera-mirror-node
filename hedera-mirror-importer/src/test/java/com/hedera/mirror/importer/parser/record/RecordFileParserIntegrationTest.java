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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlProperties;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

class RecordFileParserIntegrationTest extends IntegrationTest {

    private final static EntityId NODE_ACCOUNT_ID = EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT);

    @Value("classpath:data/recordstreams/v2/record0.0.3/2019-08-30T18_10_00.419072Z.rcd")
    Path recordFilePath1;

    @Value("classpath:data/recordstreams/v2/record0.0.3/2019-08-30T18_10_05.249678Z.rcd")
    Path recordFilePath2;

    @Resource
    private RecordFileParser recordFileParser;

    @Resource
    private CryptoTransferRepository cryptoTransferRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Resource
    private RecordFileReader recordFileReader;

    private RecordFileDescriptor recordFileDescriptor1;
    private RecordFileDescriptor recordFileDescriptor2;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private SqlProperties sqlProperties;

    @BeforeEach
    void before() {
        RecordFile recordFile1 = recordFile(recordFilePath1.toFile(), 0L);
        RecordFile recordFile2 = recordFile(recordFilePath2.toFile(), 1L);
        recordFileDescriptor1 = new RecordFileDescriptor(93, 8, recordFile1);
        recordFileDescriptor2 = new RecordFileDescriptor(75, 5, recordFile2);

        mirrorProperties.setStartDate(Instant.EPOCH);
    }

    @Test
    void parse() {
        // when
        recordFileParser.parse(recordFileDescriptor1.getRecordFile());

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);

        // when parse second file
        recordFileParser.parse(recordFileDescriptor2.getRecordFile());

        // then
        verifyFinalDatabaseState(recordFileDescriptor1, recordFileDescriptor2);
    }

    @Test
    void rollbackSequentialIngestion() {
        rollback();
    }

    @Test
    void rollbackParallelIngestion() {
        boolean defaultIngestion = sqlProperties.isParallelIngestion();
        try {
            sqlProperties.setParallelIngestion(true);
            rollback();
        } finally {
            sqlProperties.setParallelIngestion(defaultIngestion);
        }
    }

    private void rollback() {
        // when
        RecordFile recordFile = recordFileDescriptor1.getRecordFile();
        recordFileParser.parse(recordFile);

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);

        // when
        RecordFile recordFile2 = recordFileDescriptor2.getRecordFile();
        recordFile2.setDigestAlgorithm(null); // trigger db schema violation
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> recordFileParser.parse(recordFile2));

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);
    }

    void verifyFinalDatabaseState(RecordFileDescriptor... recordFileDescriptors) {
        int cryptoTransferCount = 0;
        int entityCount = 0;
        int transactionCount = 0;
        String lastHash = "";

        for (RecordFileDescriptor descriptor : recordFileDescriptors) {
            cryptoTransferCount += descriptor.getCryptoTransferCount();
            entityCount += descriptor.getEntityCount();
            transactionCount += descriptor.getRecordFile().getCount().intValue();
            lastHash = descriptor.getRecordFile().getHash();
        }
        assertEquals(transactionCount, transactionRepository.count());
        assertEquals(cryptoTransferCount, cryptoTransferRepository.count());
        assertEquals(entityCount, entityRepository.count());

        Iterable<RecordFile> recordFiles = recordFileRepository.findAll();
        assertThat(recordFiles).usingElementComparatorOnFields("name").
                containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(recordFileDescriptors).map(RecordFileDescriptor::getRecordFile).collect(
                                Collectors.toList()))
                .allSatisfy(rf -> {
                    assertThat(rf.getLoadStart()).isGreaterThan(0L);
                    assertThat(rf.getLoadEnd()).isGreaterThan(0L);
                    assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
                }).last().extracting(RecordFile::getHash).isEqualTo(lastHash);
    }

    RecordFile recordFile(File file, long index) {
        RecordFile recordFile = recordFileReader.read(StreamFileData.from(file));
        recordFile.setIndex(index);
        recordFile.setNodeAccountId(NODE_ACCOUNT_ID);
        return recordFile;
    }

    @lombok.Value
    static class RecordFileDescriptor {
        int cryptoTransferCount;
        int entityCount;
        RecordFile recordFile;
    }
}
