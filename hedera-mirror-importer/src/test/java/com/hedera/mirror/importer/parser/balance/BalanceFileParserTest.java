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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.AccountBalanceSet;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.AccountBalanceSetRepository;

public class BalanceFileParserTest extends IntegrationTest {

    @TempDir
    Path dataPath;

    @Value("classpath:data")
    private Path sourcePath;

    @Resource
    private BalanceFileParser balanceFileParser;

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Resource
    private AccountBalanceSetRepository accountBalanceSetRepository;

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Resource
    private BalanceParserProperties parserProperties;

    private BalanceFile balanceFile1;
    private BalanceFile balanceFile2;
    private FileCopier fileCopier;

    @BeforeEach
    void setup() {
        balanceFile1 = new BalanceFile(1567188900016002001L, 25391, "2019-08-30T18_15_00.016002001Z_Balances.csv");
        balanceFile2 = new BalanceFile(1567189800010147001L, 25391, "2019-08-30T18_30_00.010147001Z_Balances.csv");

        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.setKeepFiles(false);
        parserProperties.init();

        StreamType streamType = StreamType.BALANCE;
        fileCopier = FileCopier
                .create(sourcePath, dataPath)
                .from(streamType.getPath(), "v1", "balance0.0.3")
                .filterFiles("*.csv")
                .to(streamType.getPath(), streamType.getValid());
    }

    @Test
    void noFiles() throws Exception {
        balanceFileParser.parse();

        assertParsedFiles();
        assertAccountBalances();
    }

    @Test
    void discardFiles() throws Exception {
        insertAccountBalanceFiles(balanceFile1, balanceFile2);
        fileCopier.copy();

        balanceFileParser.parse();

        assertValidFiles();
        assertParsedFiles();
        assertAccountBalances(balanceFile1, balanceFile2);
    }

    @Test
    void keepFiles() throws Exception {
        insertAccountBalanceFiles(balanceFile1, balanceFile2);
        parserProperties.setKeepFiles(true);
        fileCopier.copy();

        balanceFileParser.parse();

        assertValidFiles();
        assertParsedFiles(balanceFile1, balanceFile2);
        assertAccountBalances(balanceFile1, balanceFile2);
    }

    @Test
    void invalidFile() throws Exception {
        insertAccountBalanceFiles(balanceFile2);
        fileCopier.copy();
        File file1 = fileCopier.getTo().resolve(balanceFile1.getFilename()).toFile();
        FileUtils.writeStringToFile(file1, "corrupt", "UTF-8");

        balanceFileParser.parse();

        assertValidFiles(balanceFile1);
        assertParsedFiles();
        assertAccountBalances(balanceFile2); // Latest is processed first, so it succeeds
    }

    @Test
    void missingFileInDb() throws Exception {
        fileCopier.copy();

        balanceFileParser.parse();

        assertParsedFiles();
        assertAccountBalances();
    }

    void assertParsedFiles(BalanceFile... balanceFiles) throws Exception {
        assertFiles(parserProperties.getParsedPath(), balanceFiles);
    }

    void assertValidFiles(BalanceFile... balanceFiles) throws Exception {
        assertFiles(parserProperties.getValidPath(), balanceFiles);
    }

    void assertFiles(Path path, BalanceFile... balanceFiles) throws Exception {
        if (balanceFiles.length == 0) {
            assertThat(path).isEmptyDirectory();
            return;
        }

        List<String> filenames = Arrays.stream(balanceFiles).map(BalanceFile::getFilename).collect(Collectors.toList());
        assertThat(Files.walk(path))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(balanceFiles.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .containsAll(filenames);
    }

    void assertAccountBalances(BalanceFile... balanceFiles) {
        IterableAssert<AccountBalanceSet> absIterableAssert = assertThat(accountBalanceSetRepository.findAll())
                .hasSize(balanceFiles.length)
                .allMatch(abs -> abs.isComplete())
                .allMatch(abs -> abs.getProcessingEndTimestamp() != null)
                .allMatch(abs -> abs.getProcessingStartTimestamp() != null);

        IterableAssert<AccountBalanceFile> abfIterableAssert = assertThat(accountBalanceFileRepository.findAll())
                .hasSize(balanceFiles.length)
                .allMatch(abf -> abf.getLoadStart() > 0L)
                .allMatch(abf -> abf.getLoadEnd() > 0L);

        for (BalanceFile balanceFile : balanceFiles) {
            absIterableAssert.anyMatch(abs -> balanceFile.getConsensusTimestamp() == abs.getConsensusTimestamp() &&
                    accountBalanceRepository.findByIdConsensusTimestamp(abs.getConsensusTimestamp()).size() ==
                            balanceFile.getCount());
            abfIterableAssert.anyMatch(abf -> balanceFile.getConsensusTimestamp() == abf.getConsensusTimestamp());
        }
    }

    void insertAccountBalanceFiles(BalanceFile... balanceFiles) {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        for (BalanceFile bf : balanceFiles) {
            AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder()
                    .consensusTimestamp(bf.getConsensusTimestamp() - 1)
                    .count(0L)
                    .fileHash(bf.getFilename())
                    .loadEnd(0L)
                    .loadStart(0L)
                    .name(bf.getFilename())
                    .nodeAccountId(nodeAccountId)
                    .build();
            accountBalanceFileRepository.save(accountBalanceFile);
        }
    }
}
