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
import static com.hedera.mirror.common.domain.entity.EntityType.FILE;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static com.hedera.mirror.common.domain.entity.EntityType.UNKNOWN;
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
import com.hedera.mirror.importer.EnabledIfV2;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class HistoricalBalanceServiceIntegrationTest extends IntegrationTest {

    private static final String[] ACCOUNT_BALANCE_FILE_IGNORE_FIELDS = new String[] {"loadStart", "loadEnd", "name"};

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityRepository entityRepository;
    private final HistoricalBalanceProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final TimePartitionService timePartitionService;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;

    private Entity account;
    private TokenAccount tokenAccount;

    private List<Entity> entities = new ArrayList<>();
    private List<TokenAccount> tokenAccounts = new ArrayList<>();

    void setup() {
        // common database setup
        var balanceTimestamp = domainBuilder.timestamp() + Durations.ONE_MINUTE.toNanos();
        var treasuryAccount =
                domainBuilder.entity().customize(e -> e.id(2L).num(2L)).persist();
        account = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(
                        e -> e.balanceTimestamp(balanceTimestamp).deleted(null).type(CONTRACT))
                .persist();
        var fileWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).type(FILE))
                .persist();
        var unknownWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).type(UNKNOWN))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).deleted(true))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(balanceTimestamp).balance(null))
                .persist();
        domainBuilder
                .entity()
                .customize(
                        e -> e.balance(null).balanceTimestamp(balanceTimestamp).type(TOPIC))
                .persist();
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(balanceTimestamp))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(false).balanceTimestamp(balanceTimestamp))
                .persist();

        // Only entities with valid balance
        entities.addAll(Stream.of(treasuryAccount, account, contract, fileWithBalance, unknownWithBalance)
                .toList());
        tokenAccounts.add(tokenAccount);
    }

    @AfterEach
    void resetProperties() {
        properties.setTokenBalances(true);
        properties.setDeduplicate(false);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true", "false,false"})
    void generate(boolean tokenBalances, boolean isDeduplication) {
        // given
        setup();
        properties.setTokenBalances(tokenBalances);
        properties.setDeduplicate(isDeduplication);

        // when
        parseRecordFile(1L);
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
        account.setBalanceTimestamp(balanceTimestamp + 1);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 5);
        tokenAccount.setBalanceTimestamp(balanceTimestamp + 1);
        tokenAccountRepository.save(tokenAccount);
        // new entity, tokenAccount
        entities.add(domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(account.getBalanceTimestamp()))
                .persist());
        tokenAccounts.add(domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(account.getBalanceTimestamp()))
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

        // when
        // balances change again
        account.setBalance(account.getBalance() + 1);
        account.setBalanceTimestamp(balanceTimestamp + 1);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 1);
        tokenAccount.setBalanceTimestamp(balanceTimestamp + 1);
        tokenAccountRepository.save(tokenAccount);
        // new entity, tokenAccount
        var additionalEntity = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        entities.add(additionalEntity);
        tokenAccounts.add(domainBuilder
                .tokenAccount()
                .customize(ta -> ta.balanceTimestamp(additionalEntity.getBalanceTimestamp())
                        .accountId(additionalEntity.getId()))
                .persist());

        // then
        // process a record file which should trigger the next balance snapshot
        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        verifyGeneratedBalances(balanceTimestamp);
    }

    @Test
    void generateWhenAccountBalanceFileTableNotEmpty() {
        // given
        setup();
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
        setup();
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
        setup();
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

    @EnabledIfV2
    @Test
    void deduplicationPartitionBoundary() {
        properties.setTokenBalances(true);
        properties.setDeduplicate(true);
        var treasuryAccount =
                domainBuilder.entity().customize(e -> e.id(2L).num(2L)).persist();
        entities.add(treasuryAccount);
        parseRecordFile(1L);

        var partitions = timePartitionService.getTimePartitions("account_balance");
        var priorPartition = partitions.get(partitions.size() - 2);
        var currentPartition = partitions.get(partitions.size() - 1);
        long balanceTimestamp = priorPartition.getTimestampRange().lowerEndpoint()
                + properties.getMinFrequency().plusSeconds(1).toNanos();

        long finalBalanceTimestamp = balanceTimestamp;
        var account = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(finalBalanceTimestamp))
                .persist();
        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account.getId()).balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        tokenAccounts.add(tokenAccount);
        entities.add(account);

        var account2 = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(account.getBalanceTimestamp()))
                .persist();
        entities.add(account2);
        tokenAccounts.add(domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account2.getId()).balanceTimestamp(account2.getBalanceTimestamp()))
                .persist());

        // All balances are added to account_balance
        verifyGeneratedBalances(balanceTimestamp);

        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        // Only the treasury account is added as no other accounts have had balance updates
        verifyGeneratedBalances(balanceTimestamp);

        account.setBalance(account.getBalance() + 5);
        account.setBalanceTimestamp(balanceTimestamp + 1);
        entityRepository.save(account);
        tokenAccount.setBalance(tokenAccount.getBalance() + 5);
        tokenAccount.setBalanceTimestamp(balanceTimestamp + 1);
        tokenAccountRepository.save(tokenAccount);

        balanceTimestamp += properties.getMinFrequency().plusSeconds(1).toNanos();
        // Treasury account, account and tokenAccount have new entries in account_balance
        verifyGeneratedBalances(balanceTimestamp);

        // The timestamp has transitioned to a new partition, all accounts are added to account_balance
        balanceTimestamp = currentPartition.getTimestampRange().lowerEndpoint();
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
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .during(Durations.FIVE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(existingAccountBalanceFiles));
    }

    private void verifyGeneratedBalances(long balanceTimestamp) {
        var expectedAccountBalanceFiles = Lists.newArrayList(accountBalanceFileRepository.findAll());
        var expectedAccountBalances = new HashMap<Long, List<AccountBalance>>();
        for (AccountBalance accountBalance : accountBalanceRepository.findAll()) {
            if (expectedAccountBalances.containsKey(
                    accountBalance.getId().getAccountId().getNum())) {
                expectedAccountBalances
                        .get(accountBalance.getId().getAccountId().getNum())
                        .add(accountBalance);
            } else {
                expectedAccountBalances.put(
                        accountBalance.getId().getAccountId().getNum(), Lists.newArrayList(accountBalance));
            }
        }
        var expectedTokenBalances = new HashMap<Pair<EntityId, EntityId>, List<TokenBalance>>();
        for (TokenBalance tokenBalance : tokenBalanceRepository.findAll()) {
            var key = Pair.of(
                    tokenBalance.getId().getAccountId(), tokenBalance.getId().getTokenId());
            if (expectedTokenBalances.containsKey(key)) {
                expectedTokenBalances.get(key).add(tokenBalance);
            } else {
                expectedTokenBalances.put(key, Lists.newArrayList(tokenBalance));
            }
        }

        var accountBalancePartitions = timePartitionService.getTimePartitions("account_balance");
        var lastPartition = accountBalancePartitions.isEmpty()
                ? null
                : accountBalancePartitions.get(accountBalancePartitions.size() - 1);

        // when, a record file with consensus end signaling min frequency has passed
        parseRecordFile(balanceTimestamp);

        // then, account balance, a synthetic account balance file, and token balance should generate
        var updatedEntitiesCount = 0L;
        for (Entity entity : entities) {
            var updatedAccountBalance = getAccountBalance(balanceTimestamp, entity);
            if (!expectedAccountBalances.containsKey(entity.getId())) {
                expectedAccountBalances.computeIfAbsent(entity.getId(), k -> Lists.newArrayList(updatedAccountBalance));
                updatedEntitiesCount++;
            } else if (!properties.isDeduplicate()
                    || (lastPartition != null
                            && balanceTimestamp
                                    == lastPartition.getTimestampRange().lowerEndpoint())) {
                // There is no partition or the balance timestamp is at the beginning of the last partition
                // Expect all balances
                expectedAccountBalances.get(entity.getId()).add(updatedAccountBalance);
                updatedEntitiesCount++;
            } else {
                if (updatedAccountBalance.getId().getAccountId().getId() == 2L) {
                    // Always include the treasury account
                    expectedAccountBalances.get(entity.getId()).add(updatedAccountBalance);
                    updatedEntitiesCount++;
                    continue;
                }

                var foundBalance = expectedAccountBalances.get(entity.getId()).stream()
                        .filter(accountBalance -> accountBalance.getBalance() == updatedAccountBalance.getBalance())
                        .findFirst()
                        .isPresent();
                if (!foundBalance) {
                    expectedAccountBalances.get(entity.getId()).add(updatedAccountBalance);
                    updatedEntitiesCount++;
                }
            }
        }
        expectedAccountBalanceFiles.add(AccountBalanceFile.builder()
                .consensusTimestamp(balanceTimestamp)
                .count(updatedEntitiesCount)
                .nodeId(INVALID_NODE_ID)
                .synthetic(true)
                .build());
        if (properties.isTokenBalances()) {
            var tokenBalancePartitions = timePartitionService.getTimePartitions("token_balance");
            var lastTokenBalancePartition = tokenBalancePartitions.isEmpty()
                    ? null
                    : tokenBalancePartitions.get(tokenBalancePartitions.size() - 1);
            for (var tokenAccount : tokenAccounts) {
                var tokenBalance = getTokenBalance(balanceTimestamp, tokenAccount);
                var key = Pair.of(
                        tokenBalance.getId().getAccountId(),
                        tokenBalance.getId().getTokenId());
                if (!expectedTokenBalances.containsKey(key)) {
                    expectedTokenBalances.computeIfAbsent(key, k -> Lists.newArrayList(tokenBalance));
                } else if (!properties.isDeduplicate()
                        || (lastTokenBalancePartition != null
                                && balanceTimestamp
                                        == lastTokenBalancePartition
                                                .getTimestampRange()
                                                .lowerEndpoint())) {
                    expectedTokenBalances.get(key).add(tokenBalance);
                } else {
                    var foundToken = expectedTokenBalances.get(key).stream()
                            .filter(balance -> balance.getBalance() == tokenBalance.getBalance())
                            .findFirst()
                            .isPresent();
                    if (!foundToken) {
                        expectedTokenBalances.get(key).add(tokenBalance);
                    }
                }
            }
        }
        await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> assertThat(accountBalanceFileRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ACCOUNT_BALANCE_FILE_IGNORE_FIELDS)
                        .containsExactlyInAnyOrderElementsOf(expectedAccountBalanceFiles));
        var expectedAccountBalancesList = new ArrayList<AccountBalance>();
        expectedAccountBalances.values().forEach(list -> expectedAccountBalancesList.addAll(list));
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalancesList);
        var expectedTokenBalanceList = new ArrayList<TokenBalance>();
        expectedTokenBalances.values().forEach(list -> expectedTokenBalanceList.addAll(list));
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalanceList);
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
