/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.historicalbalance;

import static com.hedera.mirror.common.domain.balance.AccountBalanceFile.INVALID_NODE_ID;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Lists;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class HistoricalBalancesServiceTest extends IntegrationTest {

    private static final String[] ACCOUNT_BALANCE_FILE_IGNORE_FIELDS = new String[] {"loadStart", "loadEnd", "name"};

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityRepository entityRepository;
    private final HistoricalBalancesProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;

    private Entity account;
    private TokenAccount tokenAccount;

    private List<Entity> entities;
    private List<TokenAccount> tokenAccounts;

    @BeforeEach
    void setup() {
        // common database setup
        account = domainBuilder.entity().persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.deleted(null).type(CONTRACT))
                .persist();
        domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        domainBuilder.entity().customize(e -> e.balance(null)).persist();
        domainBuilder.entity().customize(e -> e.type(TOPIC)).persist();
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()))
                .persist();
        domainBuilder.tokenAccount().customize(ta -> ta.associated(false)).persist();

        // Only entities with valid balance
        entities = Lists.newArrayList(account, contract);
        tokenAccounts = Lists.newArrayList(tokenAccount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void generate(boolean tokenBalances) {
        // given
        properties.setTokenBalances(tokenBalances);

        // when
        parseRecordFile(null);
        // Download and parse the first account balance file
        var firstAccountBalanceFile = accountBalanceFileRepository.save(
                domainBuilder.accountBalanceFile().get());

        // then
        verifyNoNewAccountBalanceFile(List.of(firstAccountBalanceFile));
        long balanceTimestamp = firstAccountBalanceFile.getConsensusTimestamp()
                + properties.getMinFrequency().toNanos();
        verifyGeneratedBalances(balanceTimestamp);

        // when
        // balance changes
        account.setBalance(account.getBalance() + 5);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 18);
        tokenAccountRepository.save(tokenAccount);
        // new entity, tokenAccount
        entities.add(domainBuilder.entity().persist());
        tokenAccounts.add(domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()))
                .persist());

        // process a record file which doesn't reach the next balances snapshot interval
        var existingAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        parseRecordFile(
                balanceTimestamp + properties.getMinFrequency().minusSeconds(2).toNanos());

        // then
        verifyNoNewAccountBalanceFile(existingAccountBalanceFiles);

        // when
        // process a record file which should trigger the next balance snapshot
        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        verifyGeneratedBalances(balanceTimestamp);

        // reset tokenBalances to true
        properties.setTokenBalances(true);
    }

    @Test
    void generateWhenAccountBalanceFileTableNotEmpty() {
        // given
        // account balance file table isn't empty before the run
        var firstAccountBalanceFile = domainBuilder.accountBalanceFile().persist();
        var latestAccountBalanceFile = domainBuilder.accountBalanceFile().persist();

        // when
        parseRecordFile(null);

        // then
        verifyNoNewAccountBalanceFile(List.of(firstAccountBalanceFile, latestAccountBalanceFile));
        verifyGeneratedBalances(latestAccountBalanceFile.getConsensusTimestamp()
                + properties.getMinFrequency().toNanos());
    }

    @Test
    void generateWhenNoAccountBalanceFiles() {
        // given
        // there's no account balance files at all. The first balances snapshot will be generated in min frequency +
        // initial delay relative to the first record file's consensus end

        // when
        // parse the fist record file and a following record file which doesn't hit the required interval
        var firstRecordFile = parseRecordFile(null);
        long consensusEnd = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .minusSeconds(3)
                        .toNanos();
        parseRecordFile(consensusEnd);

        // then
        verifyNoNewAccountBalanceFile(Collections.emptyList());

        // when, then
        long balanceTimestamp = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .toNanos();
        verifyGeneratedBalances(balanceTimestamp);
    }

    @Test
    void generateWhenNoAccountBalanceFilesAndFirstRecordFileInDb() {
        // given
        var firstRecordFile = domainBuilder.recordFile().persist();

        // when
        // next record file doesn't hit the required interval
        long consensusEnd = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .minusSeconds(3)
                        .toNanos();
        parseRecordFile(consensusEnd);

        // then
        verifyNoNewAccountBalanceFile(Collections.emptyList());

        // when, then
        long balanceTimestamp = firstRecordFile.getConsensusEnd()
                + properties
                        .getMinFrequency()
                        .plus(properties.getInitialDelay())
                        .toNanos();
        verifyGeneratedBalances(balanceTimestamp);
    }

    private RecordFile parseRecordFile(final Long consensusEnd) {
        return transactionTemplate.execute(t -> {
            var recordFile = domainBuilder
                    .recordFile()
                    .customize(rf -> {
                        if (consensusEnd != null) {
                            rf.consensusEnd(consensusEnd).consensusStart(consensusEnd - 100);
                        }
                    })
                    .get();
            recordFileRepository.save(recordFile);
            applicationEventPublisher.publishEvent(new RecordFileParsedEvent(this, recordFile.getConsensusEnd()));
            return recordFile;
        });
    }

    private void verifyNoNewAccountBalanceFile(List<AccountBalanceFile> existingAccountBalanceFiles) {
        // best effort to check that no new account balance file for 500ms
        await().pollInterval(Duration.ofMillis(100))
                .during(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(existingAccountBalanceFiles));
    }

    private void verifyGeneratedBalances(long balanceTimestamp) {
        var expectedAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        var expectedAccountBalances = Lists.newArrayList(accountBalanceRepository.findAll());
        var expectedTokenBalances = Lists.newArrayList(tokenBalanceRepository.findAll());

        // when, a record file with consensus end signaling min frequency has passed
        parseRecordFile(balanceTimestamp);

        // then, a synthetic account balance file, account balance, and token balance should generate
        expectedAccountBalanceFiles.add(AccountBalanceFile.builder()
                .consensusTimestamp(balanceTimestamp)
                .count((long) entities.size())
                .nodeId(INVALID_NODE_ID)
                .synthetic(true)
                .build());
        entities.forEach(entity -> expectedAccountBalances.add(getAccountBalance(balanceTimestamp, entity)));
        if (properties.isTokenBalances()) {
            tokenAccounts.forEach(ta -> expectedTokenBalances.add(getTokenBalance(balanceTimestamp, ta)));
        }
        await().pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(expectedAccountBalanceFiles));
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
    }

    private static AccountBalance getAccountBalance(long consensusTimestamp, Entity entity) {
        return AccountBalance.builder()
                .balance(entity.getBalance())
                .id(new Id(consensusTimestamp, entity.toEntityId()))
                .build();
    }

    private static TokenBalance getTokenBalance(long consensusTimestamp, TokenAccount tokenAccount) {
        var accountId = EntityId.of(tokenAccount.getAccountId());
        var tokenId = EntityId.of(tokenAccount.getTokenId());
        return TokenBalance.builder()
                .balance(tokenAccount.getBalance())
                .id(new TokenBalance.Id(consensusTimestamp, accountId, tokenId))
                .build();
    }
}
