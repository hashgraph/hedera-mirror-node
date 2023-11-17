/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TREASURY;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AccountBalanceRepositoryTest extends AbstractRepositoryTest {

    private final AccountBalanceRepository accountBalanceRepository;
    private final EntityRepository entityRepository;

    @Test
    void balanceSnapshot() {
        long timestamp = 100;
        assertThat(accountBalanceRepository.balanceSnapshot(timestamp)).isZero();
        assertThat(accountBalanceRepository.findAll()).isEmpty();

        var account = domainBuilder.entity().persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.deleted(null).type(EntityType.CONTRACT))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(null).balance(null))
                .persist();
        var deletedAccount = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).deleted(true))
                .persist();
        var fileWithBalance =
                domainBuilder.entity().customize(e -> e.type(EntityType.FILE)).persist();
        var unknownWithBalance = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.UNKNOWN))
                .persist();
        domainBuilder.topic().persist();

        var expected = Stream.of(account, contract, deletedAccount, fileWithBalance, unknownWithBalance)
                .map(e -> AccountBalance.builder()
                        .balance(e.getBalance())
                        .id(new AccountBalance.Id(timestamp, e.toEntityId()))
                        .build())
                .toList();
        assertThat(accountBalanceRepository.balanceSnapshot(timestamp)).isEqualTo(expected.size());
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void balanceSnapshotWithDeletedAccounts() {
        // given
        long lastSnapshotTimestamp = domainBuilder.timestamp();
        var treasuryAccountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(lastSnapshotTimestamp, EntityId.of(TREASURY))))
                .persist();
        // deleted before last snapshot, will not appear in full snapshot
        domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(lastSnapshotTimestamp - 1)
                        .deleted(true)
                        .timestampRange(Range.atLeast(lastSnapshotTimestamp - 1)))
                .persist();
        var account = domainBuilder.entity().persist();
        var deletedAccount = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).deleted(true))
                .persist();
        long newSnapshotTimestamp = domainBuilder.timestamp();
        var expected = List.of(
                treasuryAccountBalance,
                buildAccountBalance(account, newSnapshotTimestamp),
                buildAccountBalance(deletedAccount, newSnapshotTimestamp));

        // when
        accountBalanceRepository.balanceSnapshot(newSnapshotTimestamp);

        // then
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void balanceSnapshotDeduplicate() {
        long lowerRangeTimestamp = 0L;
        long timestamp = 100;
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(lowerRangeTimestamp, timestamp))
                .isZero();
        assertThat(accountBalanceRepository.findAll()).isEmpty();

        // 0.0.2 is always included in the balance snapshot regardless of balance timestamp
        var treasuryAccount = domainBuilder
                .entity()
                .customize(e -> e.id(2L).num(2L).balanceTimestamp(1L))
                .persist();
        var account =
                domainBuilder.entity().customize(e -> e.balanceTimestamp(1L)).persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(1L).deleted(null).type(EntityType.CONTRACT))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.balance(null).balanceTimestamp(null))
                .persist();
        var fileWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(1L).type(EntityType.FILE))
                .persist();
        var unknownWithBalance = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(1L).type(EntityType.UNKNOWN))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.balance(null).balanceTimestamp(null).type(EntityType.TOPIC))
                .persist();

        var expected = Stream.of(treasuryAccount, account, contract, fileWithBalance, unknownWithBalance)
                .map(e -> buildAccountBalance(e, timestamp))
                .collect(Collectors.toList());

        // Update Balance Snapshot includes all balances
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(lowerRangeTimestamp, timestamp))
                .isEqualTo(expected.size());
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        expected.add(buildAccountBalance(treasuryAccount, timestamp + 1));
        // Update will always insert an entry for the treasuryAccount
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(timestamp, timestamp + 1))
                .isOne();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp2 = 200;
        account.setBalance(account.getBalance() + 1);
        account.setBalanceTimestamp(timestamp2);
        entityRepository.save(account);
        expected.add(buildAccountBalance(account, timestamp2));
        expected.add(buildAccountBalance(treasuryAccount, timestamp2));

        // Insert the new entry for account and also insert an entry for the treasuryAccount
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(timestamp, timestamp2))
                .isEqualTo(2);
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp3 = 300;
        var account2 = domainBuilder
                .entity()
                .customize(e -> e.balanceTimestamp(timestamp3))
                .persist();
        expected.add(buildAccountBalance(account2, timestamp3));
        treasuryAccount.setBalance(treasuryAccount.getBalance() + 1);
        treasuryAccount.setBalanceTimestamp(timestamp3);
        entityRepository.save(treasuryAccount);
        expected.add(buildAccountBalance(treasuryAccount, timestamp3));
        // Updates only account2 and treasuryAccount
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(timestamp2, timestamp3))
                .isEqualTo(2);
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp4 = 400;
        account.setBalance(account.getBalance() + 1);
        account.setBalanceTimestamp(timestamp4);
        entityRepository.save(account);
        expected.add(buildAccountBalance(treasuryAccount, timestamp4));
        // Update with timestamp equal to the max consensus timestamp, only the treasury account will be updated
        assertThat(accountBalanceRepository.balanceSnapshotDeduplicate(timestamp4, timestamp4))
                .isOne();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void getMaxConsensusTimestampInRange() {
        // With no account balances present the max consensus timestamp is 0
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(0L, 10L))
                .isEmpty();

        domainBuilder
                .accountBalance()
                .customize(a -> a.id(new AccountBalance.Id(5L, EntityId.of(1))))
                .persist();
        // With no treasury account present the max consensus timestamp is empty
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(0L, 10L))
                .isEmpty();

        var treasuryId = EntityId.of(2);
        domainBuilder
                .accountBalance()
                .customize(a -> a.id(new AccountBalance.Id(3L, treasuryId)))
                .persist();
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(0L, 10L))
                .get()
                .isEqualTo(3L);

        // Only the max timestamp is returned
        domainBuilder
                .accountBalance()
                .customize(a -> a.id(new AccountBalance.Id(5L, treasuryId)))
                .persist();
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(0L, 10L))
                .get()
                .isEqualTo(5L);

        // Only the timestamp within the range is returned, also verifies lower is inclusive and upper is exclusive
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(3L, 5L))
                .get()
                .isEqualTo(3L);

        // Outside the lower range
        assertThat(accountBalanceRepository.getMaxConsensusTimestampInRange(10L, 20L))
                .isEmpty();
    }

    @Test
    void findByConsensusTimestamp() {
        AccountBalance accountBalance1 = create(1L, 1, 100, 0);
        AccountBalance accountBalance2 = create(1L, 2, 200, 3);
        create(2L, 1, 50, 1);

        List<AccountBalance> result = accountBalanceRepository.findByIdConsensusTimestamp(1);
        assertThat(result).containsExactlyInAnyOrder(accountBalance1, accountBalance2);
    }

    private AccountBalance create(long consensusTimestamp, int accountNum, long balance, int numberOfTokenBalances) {
        AccountBalance.Id id = new AccountBalance.Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setAccountId(EntityId.of(0, 0, accountNum));

        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setBalance(balance);
        accountBalance.setId(id);
        accountBalance.setTokenBalances(
                createTokenBalances(consensusTimestamp, accountNum, balance, numberOfTokenBalances));
        return accountBalanceRepository.save(accountBalance);
    }

    private List<TokenBalance> createTokenBalances(
            long consensusTimestamp, int accountNum, long balance, int numberOfBalances) {
        List<TokenBalance> tokenBalanceList = new ArrayList<>();
        for (int i = 1; i <= numberOfBalances; i++) {
            TokenBalance tokenBalance = new TokenBalance();
            TokenBalance.Id id = new TokenBalance.Id();
            id.setAccountId(EntityId.of(0, 0, accountNum));
            id.setConsensusTimestamp(consensusTimestamp);
            id.setTokenId(EntityId.of(0, 1, i));
            tokenBalance.setBalance(balance);
            tokenBalance.setId(id);
            tokenBalanceList.add(tokenBalance);
        }
        return tokenBalanceList;
    }

    private AccountBalance buildAccountBalance(Entity entity, long timestamp) {
        return AccountBalance.builder()
                .balance(entity.getBalance())
                .id(new AccountBalance.Id(timestamp, entity.toEntityId()))
                .build();
    }
}
