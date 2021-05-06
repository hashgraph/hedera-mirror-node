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
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Longs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.StreamFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;

class AccountBalanceFileParserTest extends IntegrationTest {

    @TempDir
    Path dataPath;

    @Resource
    private StreamFileParser<AccountBalanceFile> accountBalanceFileParser;

    @Resource
    private AccountBalanceFileRepository accountBalanceFileRepository;

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Resource
    private TokenBalanceRepository tokenBalanceRepository;

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

    @Disabled("Fails in CI")
    @Test
    void success() throws Exception {
        AccountBalanceFile accountBalanceFile = accountBalanceFile(1);
        accountBalanceFileParser.parse(accountBalanceFile);
        assertFilesystem();
        assertAccountBalances(accountBalanceFile);
    }

    @Disabled("Fails in CI")
    @Test
    void duplicateFile() {
        AccountBalanceFile accountBalanceFile1 = accountBalanceFile(1);
        AccountBalanceFile accountBalanceFile2 = accountBalanceFile(1);

        accountBalanceFileParser.parse(accountBalanceFile1);
        assertThrows(ParserException.class, () -> accountBalanceFileParser.parse(accountBalanceFile2));

        assertAccountBalances(accountBalanceFile1);
    }

    @Disabled("Fails in CI")
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

    void assertAccountBalances(AccountBalanceFile... accountBalanceFiles) {
        assertThat(accountBalanceFileRepository.count()).isEqualTo(accountBalanceFiles.length);

        for (AccountBalanceFile accountBalanceFile : accountBalanceFiles) {
            for (AccountBalance accountBalance : accountBalanceFile.getItems()) {
                assertThat(accountBalanceRepository.findById(accountBalance.getId())).get().isEqualTo(accountBalance);

                for (TokenBalance tokenBalance : accountBalance.getTokenBalances()) {
                    assertThat(tokenBalanceRepository.findById(tokenBalance.getId())).get().isEqualTo(tokenBalance);
                }
            }

            assertThat(accountBalanceRepository.findByIdConsensusTimestamp(accountBalanceFile.getConsensusTimestamp()))
                    .hasSize(accountBalanceFile.getCount().intValue())
                    .hasSize(accountBalanceFile.getItems().size());

            assertThat(accountBalanceFileRepository.findById(accountBalanceFile.getConsensusTimestamp()))
                    .get()
                    .matches(a -> a.getLoadEnd() != null)
                    .usingRecursiveComparison()
                    .ignoringFields("items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    private AccountBalanceFile accountBalanceFile(long timestamp) {
        Instant instant = Instant.ofEpochSecond(0, timestamp);
        String filename = StreamFilename.getFilename(StreamType.BALANCE, DATA, instant);
        return AccountBalanceFile.builder()
                .bytes(Longs.toByteArray(timestamp))
                .consensusTimestamp(timestamp)
                .count(2L)
                .fileHash("fileHash" + timestamp)
                .items(List.of(accountBalance(timestamp, 1), accountBalance(timestamp, 2)))
                .loadEnd(null)
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
