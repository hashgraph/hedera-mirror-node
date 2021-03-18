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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Longs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.hedera.mirror.importer.domain.StreamFilename;

import org.assertj.core.api.IterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.AccountBalanceSet;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.AccountBalanceSetRepository;

class AccountBalanceFileParserTest extends IntegrationTest {

    @TempDir
    Path dataPath;

    @Resource
    private AccountBalanceFileParser accountBalanceFileParser;

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Resource
    private AccountBalanceSetRepository accountBalanceSetRepository;

    @Resource
    private BalanceParserProperties parserProperties;

    @BeforeEach
    void setup() {
        parserProperties.getMirrorProperties().setDataPath(dataPath);
        parserProperties.setEnabled(true);
        parserProperties.setKeepFiles(false);
    }

    @Test
    void disabled() throws Exception {
        parserProperties.setEnabled(false);
        AccountBalanceFile accountBalanceFile = accountBalanceFile(1);
        accountBalanceFileParser.parse(accountBalanceFile);
        assertFilesystem();
        assertAccountBalances();
    }

    @Test
    void success() throws Exception {
        AccountBalanceFile accountBalanceFile = accountBalanceFile(1);
        accountBalanceFileParser.parse(accountBalanceFile);
        assertFilesystem();
        assertAccountBalances(accountBalanceFile);
    }

    @Test
    void inconsistentTimestamp() {
        AccountBalanceFile accountBalanceFile = accountBalanceFile(1);
        accountBalanceFile.setConsensusTimestamp(2L);
        accountBalanceFileParser.parse(accountBalanceFile);

        assertThat(accountBalanceFileRepository.findAll()).containsExactly(accountBalanceFile);
    }

    @Test
    void badFileSkipped() {
        AccountBalanceFile accountBalanceFile1 = accountBalanceFile(1);
        AccountBalanceFile accountBalanceFile2 = accountBalanceFile(1);
        AccountBalanceFile accountBalanceFile3 = accountBalanceFile(2);

        accountBalanceFileParser.parse(accountBalanceFile1);
        accountBalanceFileParser.parse(accountBalanceFile2); // Will be skipped due to duplicate timestamp
        accountBalanceFileParser.parse(accountBalanceFile3);

        assertAccountBalances(accountBalanceFile1, accountBalanceFile3);
    }

    @Test
    void keepFiles() throws Exception {
        parserProperties.setKeepFiles(true);
        AccountBalanceFile accountBalanceFile = accountBalanceFile(1);
        accountBalanceFileParser.parse(accountBalanceFile);

        assertFilesystem(accountBalanceFile);
        assertAccountBalances(accountBalanceFile);
    }

    @Test
    void beforeStartDate() throws Exception {
        AccountBalanceFile accountBalanceFile = accountBalanceFile(-1L);
        accountBalanceFileParser.parse(accountBalanceFile);

        assertFilesystem();
        assertThat(accountBalanceFileRepository.findAll()).containsExactly(accountBalanceFile);
        assertThat(accountBalanceSetRepository.count()).isZero();
        assertThat(accountBalanceRepository.count()).isZero();
    }

    void assertFilesystem(AccountBalanceFile... balanceFiles) throws Exception {
        if (balanceFiles.length == 0) {
            assertThat(parserProperties.getParsedPath()).doesNotExist();
            return;
        }

        List<String> filenames = Arrays.stream(balanceFiles)
                .map(AccountBalanceFile::getName)
                .collect(Collectors.toList());
        assertThat(Files.walk(parserProperties.getParsedPath()))
                .filteredOn(p -> !p.toFile().isDirectory())
                .hasSize(balanceFiles.length)
                .extracting(Path::getFileName)
                .extracting(Path::toString)
                .containsAll(filenames);
    }

    void assertAccountBalances(AccountBalanceFile... balanceFiles) {
        IterableAssert<AccountBalanceFile> balanceFileAssert = assertThat(accountBalanceFileRepository.findAll())
                .usingElementComparatorOnFields("consensusTimestamp")
                .containsExactlyInAnyOrder(balanceFiles);

        IterableAssert<AccountBalanceSet> absIterableAssert = assertThat(accountBalanceSetRepository.findAll())
                .hasSize(balanceFiles.length)
                .allMatch(abs -> abs.isComplete())
                .allMatch(abs -> abs.getProcessingEndTimestamp() != null)
                .allMatch(abs -> abs.getProcessingStartTimestamp() != null);

        for (AccountBalanceFile balanceFile : balanceFiles) {
            absIterableAssert.anyMatch(abs -> balanceFile.getConsensusTimestamp() == abs.getConsensusTimestamp() &&
                    accountBalanceRepository.findByIdConsensusTimestamp(abs.getConsensusTimestamp()).size() ==
                            balanceFile.getCount());
            balanceFileAssert.anyMatch(abf -> balanceFile.getConsensusTimestamp() == abf.getConsensusTimestamp());
        }
    }

    private AccountBalanceFile accountBalanceFile(long timestamp) {
        String filename = StreamFilename
                .getFilename(StreamType.BALANCE, DATA, Instant.ofEpochSecond(0, timestamp));
        return AccountBalanceFile.builder()
                .bytes(Longs.toByteArray(timestamp))
                .consensusTimestamp(timestamp)
                .count(2L)
                .fileHash("fileHash" + timestamp)
                .items(List.of(accountBalance(timestamp, 1), accountBalance(timestamp, 2)))
                .loadEnd(timestamp)
                .loadStart(timestamp)
                .name(filename)
                .nodeAccountId(EntityId.of("0.0.3", EntityTypeEnum.ACCOUNT))
                .build();
    }

    private AccountBalance accountBalance(long timestamp, int offset) {
        EntityId accountId = EntityId.of(0, 0, offset + 1000, EntityTypeEnum.ACCOUNT);
        EntityId tokenId = EntityId.of(0, 0, offset + 2000, EntityTypeEnum.ACCOUNT);

        TokenBalance tokenBalance = new TokenBalance();
        tokenBalance.setBalance(offset);
        tokenBalance.setId(new TokenBalance.Id(timestamp, accountId, tokenId));

        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setBalance(offset);
        accountBalance.setId(new AccountBalance.Id(timestamp, accountId));
        accountBalance.setTokenBalances(List.of(tokenBalance));
        return accountBalance;
    }
}
