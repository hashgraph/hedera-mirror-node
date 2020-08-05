package com.hedera.mirror.importer.parser.balance;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.AccountBalanceSetRepository;

public class AccountBalancesFileLoaderTest extends IntegrationTest {

    @TempDir
    Path dataPath;

    @Value("classpath:data")
    private Path sourcePath;

    @Resource
    private AccountBalancesFileLoader loader;

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Resource
    private AccountBalanceSetRepository accountBalanceSetRepository;

    @Resource
    private BalanceFileReaderImplV2 balanceFileReader;

    private FileCopier fileCopier;
    private BalanceFile balanceFile;
    private File testFile;

    @BeforeEach
    void setup() {
        balanceFile = new BalanceFile(1567188900016002001L, 25391, "2019-08-30T18_15_00.016002001Z_Balances.csv");

        StreamType streamType = StreamType.BALANCE;
        fileCopier = FileCopier
                .create(sourcePath, dataPath)
                .from(streamType.getPath(), "balance0.0.3")
                .filterFiles(balanceFile.getFilename())
                .to(streamType.getPath(), streamType.getValid());
        testFile = fileCopier.getTo().resolve(balanceFile.getFilename()).toFile();
    }

    @Test
    void loadValidFile() {
        fileCopier.copy();

        assertThat(loader.loadAccountBalances(testFile)).isTrue();

        Map<AccountBalance.AccountBalanceId, AccountBalance> accountBalanceMap = new HashMap<>();
        accountBalanceRepository.findAll().forEach(accountBalance -> accountBalanceMap.put(accountBalance.getId(), accountBalance));
        assertThat(accountBalanceMap.size()).isEqualTo(balanceFile.getCount());
        try (var stream = balanceFileReader.read(testFile)) {
            var accountBalanceIter = stream.iterator();
            while (accountBalanceIter.hasNext()) {
                AccountBalance expected = accountBalanceIter.next();
                assertThat(accountBalanceMap.get(expected.getId())).isEqualTo(expected);
            }
        }

        assertThat(accountBalanceSetRepository.count()).isEqualTo(1);
        assertThat(accountBalanceSetRepository.findById(balanceFile.getConsensusTimestamp()).get())
                .matches(abs -> abs.isComplete())
                .matches(abs -> abs.getProcessingStartTimestamp() != null)
                .matches(abs -> abs.getProcessingEndTimestamp() != null);
    }

    @Test
    void loadEmptyFile() throws IOException {
        FileUtils.write(testFile, "", "utf-8");
        assertThat(loader.loadAccountBalances(testFile)).isFalse();
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(accountBalanceSetRepository.count()).isZero();
    }
}
