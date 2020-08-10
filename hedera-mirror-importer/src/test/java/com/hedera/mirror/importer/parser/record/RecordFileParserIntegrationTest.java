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

import static com.hedera.mirror.importer.domain.ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.Commit;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@Log4j2
//@SpringBootTest
//@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
public class RecordFileParserIntegrationTest extends IntegrationTest {

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    private RecordFileParser recordFileParser;

    @Mock
    private ApplicationStatusRepository applicationStatusRepository;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private RecordStreamFileListener recordStreamFileListener;

    @Resource
    private RecordItemListener recordItemListener;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private EntityRepository entityRepository;

    private File file;
    private FileCopier fileCopier;
    private static final int NUM_TXNS_FILE = 19;
    private static final int NUM_ENTITIES_FILE = 8;
    private static StreamFileData streamFileData;

    @BeforeEach
    void before() throws FileNotFoundException {
        var mirrorProperties = new MirrorProperties();
        mirrorProperties.setDataPath(dataPath);
        parserProperties = new RecordParserProperties(mirrorProperties);
        parserProperties.setKeepFiles(false);
        parserProperties.init();
        StreamType streamType = StreamType.RECORD;

        parserProperties.getMirrorProperties().setVerifyHashAfter(Instant.parse("2019-09-01T00:00:00.000000Z"));
        when(applicationStatusRepository.findByStatusCode(LAST_PROCESSED_RECORD_HASH)).thenReturn("");

        fileCopier = FileCopier
                .create(Path.of(getClass().getClassLoader().getResource("data").getPath()), dataPath)
                .from(streamType.getPath(), "v2", "record0.0.3")
                .filterFiles("*.rcd");
        fileCopier.copy();
        file = dataPath.resolve("2019-08-30T18_10_00.419072Z.rcd").toFile();

        streamFileData = new StreamFileData(file.toString(), new FileInputStream(file));

        recordFileParser = new RecordFileParser(applicationStatusRepository, parserProperties,
                new SimpleMeterRegistry(), recordItemListener, recordStreamFileListener);
    }

    @Test
    @Commit
    void parse() {
        // when
        recordFileParser.parse(streamFileData);

        // then
        assertEquals(NUM_TXNS_FILE, transactionRepository.count());
        assertEquals(NUM_ENTITIES_FILE, entityRepository.count());
    }
}
