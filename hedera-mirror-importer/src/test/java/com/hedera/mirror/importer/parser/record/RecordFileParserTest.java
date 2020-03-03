package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

// Class manually commits so have to manually cleanup tables
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class RecordFileParserTest extends IntegrationTest {

    @TempDir
    Path dataPath;
    @Value("classpath:data")
    Path testPath;
    @Resource
    private RecordFileParser recordFileParser;
    @Resource
    private ApplicationStatusRepository applicationStatusRepository;
    @Resource
    private TransactionRepository transactionRepository;
    @Resource
    private RecordFileRepository recordFileRepository;
    @Resource
    private RecordParserProperties parserProperties;
    private FileCopier fileCopier;
    private StreamType streamType;

    private File file1;
    private File file2;
    private static final int NUM_TXNS_FILE_1 = 19;
    private static final int NUM_TXNS_FILE_2 = 15;

    @BeforeEach
    void before() {
        streamType = parserProperties.getStreamType();
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd")
                .to(streamType.getPath(), streamType.getValid());
        file1 = parserProperties.getValidPath().resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();
        file2 = parserProperties.getValidPath().resolve("2019-08-30T18_10_05.249678Z.rcd").toFile();
    }

    @Test
    void parse() throws Exception {
        fileCopier.copy();
        recordFileParser.parse();

        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .extracting(Path::getFileName)
                .contains(Paths.get(file2.getName()))
                .contains(Paths.get(file1.getName()));

        Assertions.assertThat(transactionRepository.findAll())
                .hasSize(NUM_TXNS_FILE_1 + NUM_TXNS_FILE_2)
                .extracting(Transaction::getType)
                .containsOnlyElementsOf(Sets.newHashSet(11, 12, 14));
        assertEquals(1, recordFileRepository.findByName(file1.toString()).size());
        assertEquals(1, recordFileRepository.findByName(file2.toString()).size());
    }

    @Test
    void disabled() throws Exception {
        parserProperties.setEnabled(false);
        fileCopier.copy();
        recordFileParser.parse();
        assertThat(Files.walk(parserProperties.getParsedPath())).filteredOn(p -> !p.toFile().isDirectory()).hasSize(0);
        assertThat(transactionRepository.count()).isEqualTo(0L);
        assertEquals(0, recordFileRepository.count());
    }

    @Test
    void noFiles() throws Exception {
        recordFileParser.parse();
        assertThat(Files.walk(parserProperties.getParsedPath())).filteredOn(p -> !p.toFile().isDirectory()).hasSize(0);
        assertThat(transactionRepository.count()).isEqualTo(0L);
        assertEquals(0, recordFileRepository.count());
    }

    @Test
    void invalidFile() throws Exception {
        FileUtils.writeStringToFile(file2, "corrupt", "UTF-8");
        recordFileParser.parse();
        assertThat(Files.walk(parserProperties.getParsedPath())).filteredOn(p -> !p.toFile().isDirectory()).hasSize(0);
        assertThat(transactionRepository.count()).isEqualTo(0L);
        assertEquals(0, recordFileRepository.count());
    }

    @Test
    void hashMismatch() throws Exception {
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, "123");
        fileCopier.copy();
        recordFileParser.parse();
        assertThat(Files.walk(parserProperties.getParsedPath())).filteredOn(p -> !p.toFile().isDirectory()).hasSize(0);
        assertThat(transactionRepository.count()).isEqualTo(0L);
        assertEquals(0, recordFileRepository.count());
    }

    @Test
    void bypassHashMismatch() throws Exception {
        applicationStatusRepository.updateStatusValue(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH, "123");
        applicationStatusRepository
                .updateStatusValue(ApplicationStatusCode.RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER,
                        "2019-09-01T00:00:00.000000Z.rcd");
        fileCopier.copy();
        recordFileParser.parse();

        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(2)
                .extracting(Path::getFileName)
                .contains(Paths.get(file2.getName()))
                .contains(Paths.get(file1.getName()));

        Assertions.assertThat(transactionRepository.findAll()).hasSize(NUM_TXNS_FILE_1 + NUM_TXNS_FILE_2);
    }

    // Bad record with invalid timestamp should fail the file parsing and rollback the transaction.
    @Test
    void badTimestampLongOverflowTest() throws Exception {
        FileCopier.create(testPath, dataPath)
                .from("badTimestampLongOverflowTest")
                .to(streamType.getPath(), streamType.getValid()).copy();
        recordFileParser.parse();
        assertThat(Files.walk(parserProperties.getParsedPath())).filteredOn(p -> !p.toFile().isDirectory()).hasSize(0);
        assertThat(transactionRepository.count()).isEqualTo(0L);
        assertEquals(0, recordFileRepository.count());
    }

    @Test
    void loadRecordFileTwiceShouldSkip() throws Exception {
        // given
        recordFileParser.initConnection();
        fileCopier.copy();
        String fileName = file1.toString();
        boolean success = recordFileParser.loadRecordFile(fileName, new FileInputStream(file1), "", "");
        assertTrue(success);
        Assertions.assertThat(transactionRepository.findAll()).hasSize(NUM_TXNS_FILE_1);
        assertEquals(1, recordFileRepository.findByName(fileName).size());

        // when: load same file again
        success = recordFileParser.loadRecordFile(fileName, new FileInputStream(file1), "", "");

        // expect
        assertTrue(success);
        Assertions.assertThat(transactionRepository.findAll()).hasSize(NUM_TXNS_FILE_1);
        assertEquals(1, recordFileRepository.findByName(fileName).size());

        recordFileParser.closeConnection();
    }

    @Test
    void checkHashes() throws Exception {
        // given
        recordFileParser.initConnection();
        String expectedPrevFileHash =
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda";
        fileCopier.copy();
        String fileName = file2.toString();

        // when
        boolean success = recordFileParser
                .loadRecordFile(fileName, new FileInputStream(file2), expectedPrevFileHash, "456");

        // expect
        assertTrue(success);
        List<RecordFile> recordFileList = recordFileRepository.findByName(fileName);
        assertEquals(1, recordFileList.size());
        RecordFile recordFile = recordFileList.get(0);
        assertEquals(expectedPrevFileHash, recordFile.getPreviousHash());
        assertEquals("456", recordFile.getFileHash());

        recordFileParser.closeConnection();
    }
}
