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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
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
        file = dataPath.resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();

        streamFileData = new StreamFileData(file.toString(), new FileInputStream(file));
    }

    @Test
    void parse() {
        // when
        recordFileParser.parse(streamFileData);

        // then
        verifyFinalDatabaseState(NUM_CRYPTOS, NUM_TXNS, NUM_ENTITIES, NUM_RECORD_FILES);
    }

    @Test
    void verifyRollbackAndPostFunctionalityOnRecordFileRepositoryError() throws ParserSQLException,
            FileNotFoundException {
        // given
        RecordFile recordFile = new RecordFile();
        recordFile.setName("2019-08-30T18_10_00.419072Z.rcd");
        recordFile.setConsensusEnd(0L);
        recordFile.setConsensusStart(0L);
        recordFileRepository.save(recordFile);

        // when
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            recordFileParser.parse(streamFileData);
        });

        // then
        recordFileRepository.delete(recordFile);
        verifyFinalDatabaseState(0, 0, 0, 0);

        // verify continue functionality
        recordFileRepository.delete(recordFile);
        recordFileParser.parse(streamFileData);
        verifyFinalDatabaseState(NUM_CRYPTOS, NUM_TXNS, NUM_ENTITIES, NUM_RECORD_FILES);
    }

    void verifyFinalDatabaseState(int cryptoTransferCount, int transactionCount, int entityCount, int recordFileCount) {
        assertEquals(transactionCount, transactionRepository.count()); // pg copy populated
        assertEquals(cryptoTransferCount, cryptoTransferRepository.count()); // pg copy populated
        assertEquals(entityCount, entityRepository.count());
        assertEquals(recordFileCount, recordFileRepository.count());
    }
}
