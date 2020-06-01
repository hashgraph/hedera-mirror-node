package com.hedera.mirror.importer.parser.performance;

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

import java.nio.file.Path;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Log4j2
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@TestPropertySource
//@SpringBootTest
public class SeededDbIntegrationTest {
    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFileParser recordFileParser;

    @Resource
    private RecordParserProperties parserProperties;

    private FileCopier fileCopier;

    private StreamType streamType;

    @Resource
    private DBProperties dbProperties;

    private DBProperties dbPropertiesCache;

    @Rule
    public GenericContainer dslContainer = new GenericContainer(
            new ImageFromDockerfile()
                    .withFileFromClasspath("Dockerfile", "data/seededimage/Dockerfile")
                    .withFileFromClasspath("bucket-download-key.json", "data/bucket-download-key.json")
                    .withFileFromClasspath("postgresql.conf", "data/postgresql.conf")
                    .withBuildArg("dumpfile", "testnet_100k_pgdump.gz")
                    .withBuildArg("jsonkeyfile", "bucket-download-key.json")
                    .withFileFromClasspath("restore.sh", "data/restore.sh"))
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    @BeforeAll
    void warmUp() {
        log.debug("STart container from image {}", dslContainer);

        dslContainer.start();
        dbPropertiesCache = dbProperties.toBuilder().build();
        dbProperties.setHost("127.0.0.1");
        dbProperties.setName("mirror_node");
        dbProperties.setPassword("mirror_node_pass");
        dbProperties.setPort(dslContainer.getMappedPort(5432));

        log.debug("dbProperties were set to {}", dbProperties);
        streamType = parserProperties.getStreamType();
        parse("2020-02-09T18_30_00.000084Z.rcd");
    }

    @AfterAll
    void coolOff() {
        dbProperties.setHost(dbPropertiesCache.getHost());
        dbProperties.setName(dbPropertiesCache.getName());
        dbProperties.setPassword(dbPropertiesCache.getPassword());
        dbProperties.setPort(dbPropertiesCache.getPort());
        dslContainer.stop();
    }

    @BeforeEach
    void before() {
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
    }

    @Timeout(180)
    @Test
    public void parseAndIngestMultipleFiles60000Transactions() throws Exception {
        parse("*.rcd");
    }

    private void parse(String filePath) {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "performance")
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        recordFileParser.parse();
    }
}
