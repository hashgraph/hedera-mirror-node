package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.importer.migration.ErrataMigrationTest.BAD_TIMESTAMP1;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Longs;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.parser.StreamFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;

class AccountBalanceFileParserTest extends IntegrationTest {

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

    @Resource
    private MirrorProperties mirrorProperties;

    @BeforeEach
    void setup() {
        parserProperties.setEnabled(true);
    }

    @Test
    void disabled() {
        // given
        parserProperties.setEnabled(false);
        var accountBalanceFile = accountBalanceFile(1);
        var items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFileWhenSkipped(accountBalanceFile, items);
    }

    @Test
    void success() {
        // given
        var accountBalanceFile = accountBalanceFile(1);
        var items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    @Test
    void multipleBatches() {
        // given
        int batchSize = parserProperties.getBatchSize();
        parserProperties.setBatchSize(2);
        var accountBalanceFile = accountBalanceFile(1);
        var items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        parserProperties.setBatchSize(batchSize);
    }

    @Test
    void duplicateFile() {
        // given
        var accountBalanceFile = accountBalanceFile(1);
        var duplicate = accountBalanceFile(1);
        var items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);
        accountBalanceFileParser.parse(duplicate); // Will be ignored

        // then
        assertThat(accountBalanceFileRepository.count()).isEqualTo(1L);
        assertAccountBalanceFile(accountBalanceFile, items);
    }

    @Test
    void beforeStartDate() {
        // given
        var accountBalanceFile = accountBalanceFile(-1L);
        var items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFileWhenSkipped(accountBalanceFile, items);
    }

    @Test
    void errata() {
        // given
        var network = mirrorProperties.getNetwork();
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
        AccountBalanceFile accountBalanceFile = accountBalanceFile(BAD_TIMESTAMP1);
        List<AccountBalance> items = accountBalanceFile.getItems().collectList().block();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        assertThat(accountBalanceFile.getTimeOffset()).isEqualTo(-1);
        mirrorProperties.setNetwork(network);
    }

    void assertAccountBalanceFile(AccountBalanceFile accountBalanceFile, List<AccountBalance> accountBalances) {
        Map<TokenBalance.Id, TokenBalance> tokenBalances = accountBalances.stream()
                .map(AccountBalance::getTokenBalances)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(TokenBalance::getId, t -> t, (previous, current) -> previous));

        assertThat(accountBalanceFile.getBytes()).isNotNull();
        assertThat(accountBalanceFile.getItems().collectList().block()).containsExactlyElementsOf(accountBalances);
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(accountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenBalances.values());

        if (parserProperties.isEnabled()) {
            assertThat(accountBalanceFileRepository.findAll())
                    .hasSize(1)
                    .first()
                    .matches(a -> a.getLoadEnd() != null)
                    .usingRecursiveComparison()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    void assertAccountBalanceFileWhenSkipped(AccountBalanceFile accountBalanceFile,
                                             List<AccountBalance> accountBalances) {
        assertThat(accountBalanceFile.getBytes()).isNotNull();
        assertThat(accountBalanceFile.getItems().collectList().block()).containsExactlyElementsOf(accountBalances);
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(tokenBalanceRepository.count()).isZero();

        if (parserProperties.isEnabled()) {
            assertThat(accountBalanceFileRepository.findAll())
                    .hasSize(1)
                    .first()
                    .matches(a -> a.getLoadEnd() != null)
                    .usingRecursiveComparison()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    private AccountBalanceFile accountBalanceFile(long timestamp) {
        Instant instant = Instant.ofEpochSecond(0, timestamp);
        String filename = StreamFilename.getFilename(StreamType.BALANCE, DATA, instant);
        return AccountBalanceFile.builder()
                .bytes(Longs.toByteArray(timestamp))
                .consensusTimestamp(timestamp)
                .fileHash("fileHash" + timestamp)
                .items(Flux.just(accountBalance(timestamp, 1),
                        accountBalance(timestamp, 2),
                        accountBalance(timestamp, 3)))
                .loadEnd(null)
                .loadStart(timestamp)
                .name(filename)
                .nodeAccountId(EntityId.of("0.0.3", EntityType.ACCOUNT))
                .build();
    }

    private AccountBalance accountBalance(long timestamp, int offset) {
        EntityId accountId = EntityId.of(0, 0, offset + 1000, EntityType.ACCOUNT);
        EntityId tokenId = EntityId.of(0, 0, offset + 2000, EntityType.ACCOUNT);

        TokenBalance tokenBalance = new TokenBalance();
        tokenBalance.setBalance(offset);
        tokenBalance.setId(new TokenBalance.Id(timestamp, accountId, tokenId));

        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setBalance(offset);
        accountBalance.setId(new AccountBalance.Id(timestamp, accountId));
        accountBalance.setTokenBalances(List.of(tokenBalance, tokenBalance));
        return accountBalance;
    }
}
