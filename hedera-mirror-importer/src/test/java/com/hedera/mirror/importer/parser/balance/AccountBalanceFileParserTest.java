/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.balance;

import static com.hedera.mirror.importer.migration.ErrataMigrationTest.BAD_TIMESTAMP1;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.parser.StreamFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AccountBalanceFileParserTest extends IntegrationTest {

    private final AccountBalanceBuilder accountBalanceBuilder;
    private final AccountBalanceFileBuilder accountBalanceFileBuilder;
    private final StreamFileParser<AccountBalanceFile> accountBalanceFileParser;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final BalanceParserProperties parserProperties;
    private final MirrorProperties mirrorProperties;

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
                    .usingOverriddenEquals()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    void assertAccountBalanceFileWhenSkipped(
            AccountBalanceFile accountBalanceFile, List<AccountBalance> accountBalances) {
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
                    .usingOverriddenEquals()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    private AccountBalanceFile accountBalanceFile(long timestamp) {
        return accountBalanceFileBuilder
                .accountBalanceFile(timestamp)
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(1000L)
                        .balance(1000L)
                        .tokenBalance(1, 10000L)
                        .tokenBalance(1, 10000L) // duplicate token balance rows should be filtered by parser
                        .build())
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(2000L)
                        .balance(2000L)
                        .tokenBalance(2, 20000L)
                        .tokenBalance(2, 20000L)
                        .build())
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(3000L)
                        .balance(3000L)
                        .tokenBalance(3, 30000L)
                        .tokenBalance(3, 30000L)
                        .build())
                .build();
    }
}
