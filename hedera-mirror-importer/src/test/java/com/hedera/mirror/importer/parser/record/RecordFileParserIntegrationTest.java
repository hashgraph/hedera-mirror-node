package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.MissingFileException;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
public class RecordFileParserIntegrationTest extends IntegrationTest {

    private static final int NUM_CRYPTOS = 93;
    private static final int NUM_TXNS = 19;
    private static final int NUM_ENTITIES = 8;
    private static final int NUM_RECORD_FILES = 1;
    private static final String recordFilename = "2019-08-30T18_10_00.419072Z.rcd";

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

    private File file;
    private FileCopier fileCopier;
    private StreamFileData streamFileData;
    private RecordFile recordFile;

    @BeforeEach
    void before() throws FileNotFoundException {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        StreamType streamType = StreamType.RECORD;

        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));

        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();
        file = dataPath.resolve(recordFilename).toFile();

        streamFileData = new StreamFileData(file.toString(), new BufferedInputStream(new FileInputStream(file)));

        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        recordFile = new RecordFile(1567188600419072000L, 1567188604906443001L, null, recordFilename, 0L, 0L,
                "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda",
                "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                nodeAccountId, 0L, 2);
        recordFileRepository.save(recordFile);
    }

    @AfterEach
    void after() throws IOException {
        streamFileData.getBufferedInputStream().close();
    }

    @Test
    void parse() {
        // when
        recordFileParser.parse(streamFileData);

        // then
        verifyFinalDatabaseState(NUM_CRYPTOS, NUM_TXNS, NUM_ENTITIES, NUM_RECORD_FILES);
    }

    @Test
    void verifyRollbackAndPostFunctionalityOnRecordFileRepositoryError() {
        // given
        recordFileRepository.deleteAll();

        // when
        Assertions.assertThrows(MissingFileException.class, () -> {
            recordFileParser.parse(streamFileData);
        });

        // then
        verifyFinalDatabaseState(0, 0, 0, 0);

        // verify continue functionality
        recordFileRepository.save(recordFile);
        recordFileParser.parse(streamFileData);
        verifyFinalDatabaseState(NUM_CRYPTOS, NUM_TXNS, NUM_ENTITIES, NUM_RECORD_FILES);
    }

    void verifyFinalDatabaseState(int cryptoTransferCount, int transactionCount, int entityCount, int recordFileCount) {
        assertEquals(transactionCount, transactionRepository.count()); // pg copy populated
        assertEquals(cryptoTransferCount, cryptoTransferRepository.count()); // pg copy populated
        assertEquals(entityCount, entityRepository.count());

        Iterable<RecordFile> recordFiles = recordFileRepository.findAll();
        assertThat(recordFiles).hasSize(recordFileCount).allSatisfy(rf -> {
            assertThat(rf.getLoadStart()).isGreaterThan(0L);
            assertThat(rf.getLoadEnd()).isGreaterThan(0L);
            assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
        });
    }
}
