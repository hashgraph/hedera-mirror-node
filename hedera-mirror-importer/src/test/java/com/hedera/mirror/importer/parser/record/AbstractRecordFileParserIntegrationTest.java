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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestRecordFiles;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.MissingFileException;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@RequiredArgsConstructor
abstract class AbstractRecordFileParserIntegrationTest extends IntegrationTest {

    private final static EntityId NODE_ACCOUNT_ID = EntityId.of(TestUtils.toAccountId("0.0.3"));
    protected final static Map<String, RecordFile> ALL_RECORD_FILE_MAP = TestRecordFiles.getAll();

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFileParser recordFileParser;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private CryptoTransferRepository cryptoTransferRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private RecordFileRepository recordFileRepository;

    private final RecordFileDescriptor recordFileDescriptor1;
    private final RecordFileDescriptor recordFileDescriptor2;

    private RecordFile recordFile1;
    private RecordFile recordFile2;

    @BeforeEach
    void before() {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);

        recordFile1 = recordFileDescriptor1.getRecordFile().toBuilder().nodeAccountId(NODE_ACCOUNT_ID).build();
        recordFile2 = recordFileDescriptor2.getRecordFile().toBuilder().nodeAccountId(NODE_ACCOUNT_ID).build();

        FileCopier fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(StreamType.RECORD.getPath(), "v" + recordFile1.getVersion(), "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();
    }

    @Test
    void parse() {
        // given
        recordFileRepository.save(recordFile1);

        // when
        recordFileParser.parse(recordFile1);

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);

        // given
        recordFileRepository.save(recordFile2);

        // when parse second file
        recordFileParser.parse(recordFile2);

        // then
        verifyFinalDatabaseState(recordFileDescriptor1, recordFileDescriptor2);
    }

    @Test
    void verifyRollbackAndPostFunctionalityOnRecordFileRepositoryError() {
        // given

        // when
        Assertions.assertThrows(MissingFileException.class, () -> {
            recordFileParser.parse(recordFile1);
        });

        // then
        verifyFinalDatabaseState();

        // verify continue functionality
        recordFileRepository.save(recordFile1);
        recordFileParser.parse(recordFile1);
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

    @lombok.Value
    static class RecordFileDescriptor {
        int cryptoTransferCount;
        int entityCount;
        RecordFile recordFile;
    }
}
