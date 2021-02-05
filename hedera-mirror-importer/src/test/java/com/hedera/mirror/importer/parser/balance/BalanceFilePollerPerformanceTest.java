package com.hedera.mirror.importer.parser.balance;

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
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Tag("performance")
public class BalanceFilePollerPerformanceTest extends IntegrationTest {

    @TempDir
    static Path dataPath;

    @Value("classpath:data")
    Path testPath;

    @Resource
    private BalanceParserProperties parserProperties;

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    private FileCopier fileCopier;

    private StreamType streamType;

    @BeforeEach
    void before() throws IOException {
        streamType = parserProperties.getStreamType();
        parserProperties.getMirrorProperties().setDataPath(dataPath);

        EntityId nodeAccountId = EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT);
        Files.walk(Path.of(testPath.toString(), streamType.getPath(), "v1", "performance"))
                .filter(p -> p.toString().endsWith(".csv"))
                .forEach(p -> {
                    String filename = FilenameUtils.getName(p.toString());
                    AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder()
                            .consensusTimestamp(Utility.getTimestampFromFilename(filename))
                            .count(0L)
                            .fileHash(filename)
                            .loadEnd(0L)
                            .loadStart(0L)
                            .name(filename)
                            .nodeAccountId(nodeAccountId)
                            .build();
                    accountBalanceFileRepository.save(accountBalanceFile);
                });
    }

    @Timeout(15)
    @Test
    void parseAndIngestMultipleBalanceCsvFiles() {
        parse("*.csv");
    }

    private void parse(String filePath) {
        fileCopier = FileCopier.create(testPath, dataPath)
                .from(streamType.getPath(), "v1", "performance")
                .filterFiles(filePath)
                .to(streamType.getPath(), streamType.getValid());
        fileCopier.copy();

        //balanceFilePoller.poll();
    }
}
