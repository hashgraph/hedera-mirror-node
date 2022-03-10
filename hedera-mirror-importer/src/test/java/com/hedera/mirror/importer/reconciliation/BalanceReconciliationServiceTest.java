package com.hedera.mirror.importer.reconciliation;

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

import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.FIFTY_BILLION_HBARS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.METRIC;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_CRYPTO_TRANSFERS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_FIFTY_BILLION;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_TOKEN_TRANSFERS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.SUCCESS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.TokenAccountId;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class BalanceReconciliationServiceTest extends IntegrationTest {

    private final DomainBuilder domainBuilder;
    private final MeterRegistry meterRegistry;
    private final ReconciliationProperties reconciliationProperties;
    private final BalanceReconciliationService reconciliationService;

    @BeforeEach
    void setup() {
        reconciliationProperties.setEnabled(true);
        reconciliationProperties.setEndDate(Utility.MAX_INSTANT_LONG);
        reconciliationProperties.setStartDate(Instant.EPOCH);
        reconciliationProperties.setToken(true);
    }

    @Test
    void cryptoTransfersSuccess() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1000);
        transfer(3, 4, 100);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 900L, 4L, 100L, 5L, 0L));
        transfer(2, 4, 500);
        transfer(2, 5, 100);
        var last = balance(Map.of(2L, FIFTY_BILLION_HBARS - 1600L, 3L, 900L, 4L, 600L, 5L, 100L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
        assertStartDate(last);
    }

    @Test
    void cryptoTransfersFailure() {
        // given
        var first = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1000);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1010L, 3L, 1010L)); // Missing 10 tinybar transfer

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(FAILURE_CRYPTO_TRANSFERS);
        assertStartDate(first);
    }

    @Test
    void cryptoTransfersZeroBalances() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 3L, 0L));
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 4L, 0L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void tokenTransfersSuccess() {
        // given
        tokenBalance(Map.of());
        tokenTransfer(2, 100, 1000);
        tokenTransfer(2, 100, -100);
        tokenTransfer(3, 100, 100);
        tokenBalance(Map.of(new TokenAccountId(2, 100), 900L, new TokenAccountId(3, 100), 100L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void tokenTransfersFailure() {
        // given
        tokenBalance(Map.of(new TokenAccountId(2, 100), 1L));
        tokenBalance(Map.of(new TokenAccountId(2, 100), 2L)); // Missing transfer

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(FAILURE_TOKEN_TRANSFERS);
    }

    @Test
    void tokensNotEnabled() {
        // given
        reconciliationProperties.setToken(false);
        tokenBalance(Map.of(new TokenAccountId(2, 100), 1L));
        tokenBalance(Map.of(new TokenAccountId(2, 100), 2L)); // Missing transfer

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void tokenTransfersZeroBalances() {
        // given
        tokenBalance(Map.of(new TokenAccountId(2, 100), 0L, new TokenAccountId(3, 100), 0L));
        tokenBalance(Map.of(new TokenAccountId(2, 100), 0L, new TokenAccountId(3, 100), 0L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void errata() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1);
        domainBuilder.cryptoTransfer().customize(c -> c.amount(100).entityId(4).errata(ErrataType.DELETE)).persist();
        domainBuilder.cryptoTransfer().customize(c -> c.amount(-100).entityId(2).errata(ErrataType.DELETE)).persist();
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1L, 3L, 1L)); // Errata rows not present

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void notEnabled() {
        // given
        reconciliationProperties.setEnabled(false);
        balance(Map.of(2L, 1L)); // Would fail if checked

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void noBalanceFiles() {
        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void singleBalanceFile() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void balanceNotFiftyBillion() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 100);
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 3L, 100L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(FAILURE_FIFTY_BILLION);
    }

    @Test
    void startDate() {
        // given
        balance(Map.of(2L, 1L)); // Would fail if checked
        var balanceFile2 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        reconciliationProperties.setStartDate(Instant.ofEpochSecond(0L, balanceFile2.getConsensusTimestamp()));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void endDate() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balanceFile3 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, 1L)); // Would fail if checked
        reconciliationProperties.setEndDate(Instant.ofEpochSecond(0L, balanceFile3.getConsensusTimestamp()));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
    }

    @Test
    void recovers() {
        // given
        var first = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        domainBuilder.cryptoTransfer().customize(c -> c.amount(100).entityId(3)).persist();
        var missingTransfer = domainBuilder.cryptoTransfer().customize(c -> c.amount(-100).entityId(2));
        var last = balance(Map.of(2L, FIFTY_BILLION_HBARS - 100, 3L, 100L));

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(FAILURE_CRYPTO_TRANSFERS);
        assertStartDate(first);

        // given
        missingTransfer.persist();

        // when
        reconciliationService.reconcile();

        // then
        assertMetric(SUCCESS);
        assertStartDate(last);
    }

    private void assertMetric(BalanceReconciliationService.ReconciliationStatus status) {
        assertThat(meterRegistry.find(METRIC).gauges())
                .hasSize(1)
                .first()
                .extracting(Gauge::value)
                .isEqualTo((double) status.ordinal());
    }

    private void assertStartDate(AccountBalanceFile accountBalanceFile) {
        var expectedStartDate = Instant.ofEpochSecond(0L, accountBalanceFile.getConsensusTimestamp());
        assertThat(reconciliationProperties.getStartDate()).isEqualTo(expectedStartDate);
    }

    private AccountBalanceFile balance(Map<Long, Long> balances) {
        var accountBalanceFile = domainBuilder.accountBalanceFile().persist();
        long timestamp = accountBalanceFile.getConsensusTimestamp();

        balances.forEach((accountId, balance) -> {
            var entityId = EntityId.of(accountId, EntityType.ACCOUNT);
            domainBuilder.accountBalance()
                    .customize(a -> a.balance(balance).id(new AccountBalance.Id(timestamp, entityId)))
                    .persist();
        });

        domainBuilder.recordFile().persist();
        return accountBalanceFile;
    }

    private AccountBalanceFile tokenBalance(Map<TokenAccountId, Long> balances) {
        var accountBalanceFile = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        long timestamp = accountBalanceFile.getConsensusTimestamp();

        balances.forEach((id, balance) -> {
            var accountId = EntityId.of(id.getAccountId(), EntityType.ACCOUNT);
            var tokenId = EntityId.of(id.getTokenId(), EntityType.TOKEN);
            domainBuilder.tokenBalance()
                    .customize(a -> a.balance(balance).id(new TokenBalance.Id(timestamp, accountId, tokenId)))
                    .persist();
        });

        return accountBalanceFile;
    }

    private void transfer(long from, long to, long amount) {
        domainBuilder.cryptoTransfer().customize(c -> c.amount(amount).entityId(to)).persist();
        domainBuilder.cryptoTransfer().customize(c -> c.amount(-amount).entityId(from)).persist();
    }

    private void tokenTransfer(long accountNum, long tokenNum, long amount) {
        long timestamp = domainBuilder.timestamp();
        EntityId accountId = EntityId.of(accountNum, EntityType.ACCOUNT);
        EntityId tokenId = EntityId.of(tokenNum, EntityType.TOKEN);
        domainBuilder.tokenTransfer()
                .customize(c -> c.amount(amount).id(new TokenTransfer.Id(timestamp, tokenId, accountId)))
                .persist();
    }
}
