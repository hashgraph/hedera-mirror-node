/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class RecordFileParserIntegrationTest extends IntegrationTest {

    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final RecordFileParser recordFileParser;
    private final RecordFileReader recordFileReader;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;

    @Value("classpath:data/recordstreams/v2/record0.0.3/2019-08-30T18_10_00.419072Z.rcd")
    private final Path recordFilePath1;

    @Value("classpath:data/recordstreams/v2/record0.0.3/2019-08-30T18_10_05.249678Z.rcd")
    private final Path recordFilePath2;

    private RecordFileDescriptor recordFileDescriptor1;
    private RecordFileDescriptor recordFileDescriptor2;

    @BeforeEach
    void before() {
        RecordFile recordFile1 = recordFile(recordFilePath1.toFile(), 0L);
        RecordFile recordFile2 = recordFile(recordFilePath2.toFile(), 1L);
        recordFileDescriptor1 = new RecordFileDescriptor(83, 5, recordFile1);
        recordFileDescriptor2 = new RecordFileDescriptor(65, 5, recordFile2);
    }

    @Test
    void parse() {
        // when
        recordFileParser.parse(recordFileDescriptor1.recordFile());

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);

        // when parse second file
        recordFileParser.parse(recordFileDescriptor2.recordFile());

        // then
        verifyFinalDatabaseState(recordFileDescriptor1, recordFileDescriptor2);
    }

    @Test
    void rollback() {
        // when
        RecordFile recordFile = recordFileDescriptor1.recordFile();
        recordFileParser.parse(recordFile);

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);

        // when
        RecordFile recordFile2 = recordFileDescriptor2.recordFile();
        recordFile2.setItems(recordFile.getItems()); // Re-processing same transactions should result in duplicate keys
        Assertions.assertThrows(ParserException.class, () -> recordFileParser.parse(recordFile2));

        // then
        verifyFinalDatabaseState(recordFileDescriptor1);
        assertThat(retryRecorder.getRetries(ParserException.class)).isEqualTo(2);
    }

    void verifyFinalDatabaseState(RecordFileDescriptor... recordFileDescriptors) {
        int cryptoTransferCount = 0;
        int entityCount = 0;
        var expectedRecordFiles = new ArrayList<RecordFile>();
        int transactionCount = 0;

        for (RecordFileDescriptor descriptor : recordFileDescriptors) {
            cryptoTransferCount += descriptor.cryptoTransferCount();
            entityCount += descriptor.entityCount();
            transactionCount += descriptor.recordFile().getCount().intValue();
            expectedRecordFiles.add(descriptor.recordFile());
        }
        assertEquals(transactionCount, transactionRepository.count());
        assertEquals(cryptoTransferCount, cryptoTransferRepository.count());
        assertEquals(entityCount, entityRepository.count());

        assertThat(recordFileRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedRecordFiles)
                .allSatisfy(rf -> {
                    assertThat(rf.getLoadStart()).isPositive();
                    assertThat(rf.getLoadEnd()).isPositive();
                    assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
                });
    }

    RecordFile recordFile(File file, long index) {
        RecordFile recordFile = recordFileReader.read(StreamFileData.from(file));
        recordFile.setIndex(index);
        recordFile.setNodeId(0L);
        return recordFile;
    }

    record RecordFileDescriptor(int cryptoTransferCount, int entityCount, RecordFile recordFile) {}
}
