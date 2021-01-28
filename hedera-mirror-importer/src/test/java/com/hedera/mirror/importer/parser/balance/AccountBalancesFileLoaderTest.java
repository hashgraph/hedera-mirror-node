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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV1;
import com.hedera.mirror.importer.reader.balance.BalanceFileReaderImplV2;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.AccountBalanceSetRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;

public class AccountBalancesFileLoaderTest extends IntegrationTest {

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
    private BalanceFileReaderImplV1 balanceFileReaderV1;

    @Resource
    private BalanceFileReaderImplV2 balanceFileReaderV2;

    @Value("classpath:data/accountBalances/v1/balance0.0.3/2019-08-30T18_15_00.016002001Z_Balances.csv")
    private File balanceFileV1;

    @Value("classpath:data/accountBalances/v2/2020-09-22T04_25_00.083212003Z_Balances.csv")
    private File balanceFileV2;

    private BalanceFile balanceFileStats;

    private File testFile;

    @ParameterizedTest(name = "load balance file version 1 with range [{0}, {1}], expect data persisted? {2}")
    @CsvSource(value = {
            "2019-08-30T18:15:00.016002001Z, 2019-08-30T18:15:00.016002001Z, true",
            ",,true",
            "2019-08-30T18:15:00.016002002Z, 2019-08-30T18:15:00.016002008Z, false"
    })
    void loadValidFileVersion1(Instant start, Instant end, boolean persisted) throws SQLException {
        balanceFileStats = new BalanceFile(1567188900016002001L, 25391, balanceFileV1.getName());
        loadValidFile(balanceFileV1, balanceFileReaderV1, start, end, persisted);
    }

    @ParameterizedTest(name = "load balance file version 2 with range [{0}, {1}], expect data persisted? {2}")
    @CsvSource(value = {
            "2020-09-22T04:25:00.083212003Z, 2020-09-22T04:25:00.083212003Z, true",
            ",,true",
            "2020-09-22T04:25:00.083212004Z, 2020-09-22T04:25:00.083212009Z, false"
    })
    void loadValidFileVersion2(Instant start, Instant end, boolean persisted) throws SQLException {
        balanceFileStats = new BalanceFile(1600748700083212003L, 106, balanceFileV2.getName());
        loadValidFile(balanceFileV2, balanceFileReaderV2, start, end, persisted);
    }

    @Test
    void loadEmptyFile() throws IOException {
        testFile = Files.createTempFile(null, null).toFile();
        FileUtils.write(testFile, "", "utf-8");
        assertThrows(Exception.class, () -> {
            loader.loadAccountBalances(testFile, null);
        });
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(accountBalanceSetRepository.count()).isZero();
    }

    @Test
    void rollBackWhenMissingFileInAccountBalanceFileRepo() {
        assertThrows(Exception.class, () -> {
            loader.loadAccountBalances(testFile, null);
        });
        assertThat(accountBalanceFileRepository.count()).isZero();
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(accountBalanceSetRepository.count()).isZero();
    }

    private void loadValidFile(File balanceFile, BalanceFileReader balanceFileReader, Instant start, Instant end,
                               boolean persisted) {
        insertAccountBalanceFileRecord(balanceFile);
        DateRangeFilter filter = null;
        if (start != null || end != null) {
            filter = new DateRangeFilter(start, end);
        }

        Instant loadStart = Instant.now();

        loader.loadAccountBalances(balanceFile, filter);

        Map<AccountBalance.Id, AccountBalance> accountBalanceMap = new HashMap<>();
        accountBalanceRepository.findAll()
                .forEach(accountBalance -> accountBalanceMap.put(accountBalance.getId(), accountBalance));

        if (persisted) {
            assertThat(accountBalanceMap.size()).isEqualTo(balanceFileStats.getCount());
            try (var stream = balanceFileReader.read(balanceFile)) {
                var accountBalanceIter = stream.iterator();
                while (accountBalanceIter.hasNext()) {
                    AccountBalance expected = accountBalanceIter.next();
                    assertThat(accountBalanceMap.get(expected.getId())).usingRecursiveComparison().isEqualTo(expected);
                }
            }

            assertThat(accountBalanceSetRepository.count()).isEqualTo(1);
            assertThat(accountBalanceSetRepository.findById(balanceFileStats.getConsensusTimestamp()).get())
                    .matches(abs -> abs.isComplete())
                    .matches(abs -> abs.getProcessingStartTimestamp() != null)
                    .matches(abs -> abs.getProcessingEndTimestamp() != null);
        } else {
            assertThat(accountBalanceMap).isEmpty();
            assertThat(accountBalanceSetRepository.count()).isZero();
        }

        AccountBalanceFile accountBalanceFile = accountBalanceFileRepository
                .findById(balanceFileStats.getConsensusTimestamp()).get();
        assertAll(() -> assertThat(accountBalanceFile.getCount()).isEqualTo(balanceFileStats.getCount()),
                () -> assertThat(accountBalanceFile.getLoadStart()).isGreaterThanOrEqualTo(loadStart.getEpochSecond()),
                () -> assertThat(accountBalanceFile.getLoadEnd())
                        .isGreaterThanOrEqualTo(accountBalanceFile.getLoadStart()));
    }

    private void insertAccountBalanceFileRecord(File file) {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder()
                .consensusTimestamp(balanceFileStats.getConsensusTimestamp())
                .count(0L)
                .fileHash("fileHash")
                .loadEnd(0L)
                .loadStart(0L)
                .name(file.getName())
                .nodeAccountId(nodeAccountId)
                .build();
        accountBalanceFileRepository.save(accountBalanceFile);
    }
}
