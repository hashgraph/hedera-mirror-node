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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RequiredArgsConstructor
abstract class AbstractRecordFileParserPerformanceTest extends IntegrationTest {

    private final static EntityId DEFAULT_NODE_ACCOUNT_ID = EntityId.of(TestUtils.toAccountId("0.0.3"));

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private RecordFilePoller recordFilePoller;

    @Resource
    private RecordParserProperties parserProperties;

    @Resource
    private RecordFileRepository recordFileRepository;

    private StreamType streamType;

    private final String warmUpFile;

    private final String[] hashes;

    private final int version;

    @BeforeAll
    void warmUp() throws Exception {
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.init();
        streamType = parserProperties.getStreamType();

        RecordFile recordFile = RecordFile.builder()
                .consensusStart(Utility.getTimestampFromFilename(warmUpFile))
                .consensusEnd(0L)
                .count(0L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(warmUpFile)
                .name(warmUpFile)
                .nodeAccountId(DEFAULT_NODE_ACCOUNT_ID)
                .previousHash(warmUpFile)
                .version(version)
                .build();
        recordFileRepository.save(recordFile);
        parse(warmUpFile, true);
    }

    @BeforeEach
    void beforeEach() throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.walk(testPath.resolve(getSourceSubPath()))
                .filter(p -> p.toString().endsWith(".rcd"))
                .sorted()
                .forEach(p -> {
                    int index = count.getAndIncrement();
                    String previousHash = index == 0 ? DigestAlgorithm.SHA384.getEmptyHash() : hashes[index - 1];
                    String currentHash = hashes[index];
                    String filename = FilenameUtils.getName(p.toString());
                    long timestamp = Utility.getTimestampFromFilename(filename);
                    RecordFile recordFile = RecordFile.builder()
                            .consensusStart(timestamp)
                            .consensusEnd(0L)
                            .count(0L)
                            .digestAlgorithm(DigestAlgorithm.SHA384)
                            .fileHash(currentHash)
                            .endRunningHash(currentHash)
                            .name(filename)
                            .nodeAccountId(DEFAULT_NODE_ACCOUNT_ID)
                            .previousHash(previousHash)
                            .version(version)
                            .build();
                    recordFileRepository.save(recordFile);
                });
    }

    @Timeout(30)
    @Test
    void parseAndIngestMultipleLargeFiles() throws Exception {
        parse("*.rcd", false);
    }

    private void parse(String filePath, boolean warmup) throws Exception {
        FileCopier fileCopier = FileCopier.create(testPath, dataPath)
                .from(getSourceSubPath())
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();
        if (!warmup) {
            Files.deleteIfExists(fileCopier.getTo().resolve(warmUpFile));
        }
        recordFilePoller.poll();
        Files.deleteIfExists(fileCopier.getTo().resolve(warmUpFile));
    }

    private Path getSourceSubPath() {
        return Path.of(streamType.getPath(), "performance", "v" + version);
    }
}
