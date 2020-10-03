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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV2;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.AccountBalanceSetRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;

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
    private TokenBalanceRepository tokenBalanceRepository;

    @Resource
    private AccountBalanceSetRepository accountBalanceSetRepository;

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Resource
    private BalanceFileReaderImplV2 balanceFileReader;

    private FileCopier fileCopier;
    private BalanceFile balanceFile;
    private File testFile;

    @BeforeEach
    void setup() {
        balanceFile = new BalanceFile(1600748700083212003L, 106, "2020-09-22T04_25_00.083212003Z_Balances.csv");

        StreamType streamType = StreamType.BALANCE;
        fileCopier = FileCopier
                .create(sourcePath, dataPath)
                .from(streamType.getPath(), "v2")
                .filterFiles(balanceFile.getFilename())
                .to(streamType.getPath(), streamType.getValid());
        testFile = fileCopier.getTo().resolve(balanceFile.getFilename()).toFile();
    }

    @ParameterizedTest(name = "load balance file with range [{0}, {1}], expect data persisted? {2}")
    @CsvSource(value = {
            "2020-09-22T04:25:00.083212003Z, 2020-09-22T04:25:00.083212003Z, true",
            ",,true",
            "2020-09-22T04:25:00.083212004Z, 2020-09-22T04:25:00.083212009Z, false"
    })
    void loadValidFile(Instant start, Instant end, boolean persisted) throws SQLException {
        insertAccountBalanceFileRecord();
        fileCopier.copy();
        DateRangeFilter filter = null;
        if (start != null || end != null) {
            filter = new DateRangeFilter(start, end);
        }

        Instant loadStart = Instant.now();

        loader.loadAccountBalances(testFile, filter);

        Map<AccountBalance.Id, AccountBalance> accountBalanceMap = new HashMap<>();
        accountBalanceRepository.findAll()
                .forEach(accountBalance -> accountBalanceMap.put(accountBalance.getId(), accountBalance));

        if (persisted) {
            assertThat(accountBalanceMap.size()).isEqualTo(balanceFile.getCount());
            try (var stream = balanceFileReader.read(testFile)) {
                var accountBalanceIter = stream.iterator();
                while (accountBalanceIter.hasNext()) {
                    AccountBalance expected = accountBalanceIter.next();
                    assertThat(accountBalanceMap.get(expected.getId())).usingRecursiveComparison().isEqualTo(expected);
                }
            }

            assertThat(accountBalanceSetRepository.count()).isEqualTo(1);
            assertThat(accountBalanceSetRepository.findById(balanceFile.getConsensusTimestamp()).get())
                    .matches(abs -> abs.isComplete())
                    .matches(abs -> abs.getProcessingStartTimestamp() != null)
                    .matches(abs -> abs.getProcessingEndTimestamp() != null);
        } else {
            assertThat(accountBalanceMap).isEmpty();
            assertThat(accountBalanceSetRepository.count()).isZero();
        }

        AccountBalanceFile accountBalanceFile = accountBalanceFileRepository
                .findById(balanceFile.getConsensusTimestamp()).get();
        assertAll(() -> assertThat(accountBalanceFile.getCount()).isEqualTo(balanceFile.getCount()),
                () -> assertThat(accountBalanceFile.getLoadStart()).isGreaterThanOrEqualTo(loadStart.getEpochSecond()),
                () -> assertThat(accountBalanceFile.getLoadEnd())
                        .isGreaterThanOrEqualTo(accountBalanceFile.getLoadStart()));
    }

    @Test
    void loadEmptyFile() throws IOException {
        FileUtils.write(testFile, "", "utf-8");
        assertThrows(Exception.class, () -> {
            loader.loadAccountBalances(testFile, null);
        });
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(accountBalanceSetRepository.count()).isZero();
    }

    @Test
    void rollBackWhenMissingFileInAccountBalanceFileRepo() {
        fileCopier.copy();

        assertThrows(Exception.class, () -> {
            loader.loadAccountBalances(testFile, null);
        });
        assertThat(accountBalanceFileRepository.count()).isZero();
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(accountBalanceSetRepository.count()).isZero();
    }

    private void insertAccountBalanceFileRecord() {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder()
                .consensusTimestamp(balanceFile.getConsensusTimestamp())
                .count(0L)
                .fileHash("fileHash")
                .loadEnd(0L)
                .loadStart(0L)
                .name(balanceFile.getFilename())
                .nodeAccountId(nodeAccountId)
                .build();
        accountBalanceFileRepository.save(accountBalanceFile);
    }
}
