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

import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_CRYPTO_TRANSFERS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_FIFTY_BILLION;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.FAILURE_TOKEN_TRANSFERS;
import static com.hedera.mirror.importer.reconciliation.BalanceReconciliationService.ReconciliationStatus.SUCCESS;

import com.google.common.base.Stopwatch;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;

@Log4j2
@Named
public class BalanceReconciliationService {

    static final long FIFTY_BILLION_HBARS = 50_000_000_000L * 100_000_000L;
    static final String METRIC = "hedera.mirror.reconciliation";

    private static final String BALANCE_QUERY = "select account_id, balance from account_balance " +
            "where consensus_timestamp = ?";

    private static final String CRYPTO_TRANSFER_QUERY = "select entity_id, sum(amount) balance from crypto_transfer " +
            "where consensus_timestamp > ? and consensus_timestamp <= ? and (errata is null or errata <> 'DELETE') " +
            "group by entity_id";

    private static final String TOKEN_BALANCE_QUERY = "select account_id, token_id, balance from token_balance " +
            "where consensus_timestamp = ?";

    private static final String TOKEN_TRANSFER_QUERY = "select account_id, token_id, sum(amount) as balance " +
            "from token_transfer where consensus_timestamp > ? and consensus_timestamp <= ? " +
            "group by token_id, account_id";

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final JdbcOperations jdbcOperations;
    private final ReconciliationProperties reconciliationProperties;
    private final AtomicReference<ReconciliationStatus> status;

    public BalanceReconciliationService(AccountBalanceFileRepository accountBalanceFileRepository,
                                        JdbcOperations jdbcOperations, MeterRegistry meterRegistry,
                                        ReconciliationProperties reconciliationProperties) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.jdbcOperations = jdbcOperations;
        this.reconciliationProperties = reconciliationProperties;
        status = new AtomicReference(SUCCESS);
        meterRegistry.gauge(METRIC, status, s -> s.get().ordinal());
    }

    @Scheduled(cron = "${hedera.mirror.importer.reconciliation.cron:0 0 0 * * *}")
    public synchronized void reconcile() {
        status.set(SUCCESS);

        if (!reconciliationProperties.isEnabled()) {
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        Optional<BalanceSnapshot> previous = Optional.empty();
        Throwable t = null;

        try {
            log.info("Reconciling balance files between {} and {}",
                    reconciliationProperties.getStartDate(), reconciliationProperties.getEndDate());
            previous = getNextBalanceSnapshot(Optional.empty());

            if (previous.isEmpty()) {
                log.info("No balance files to process");
                return;
            }

            var current = getNextBalanceSnapshot(previous);

            while (current.isPresent()) {
                reconcile(previous.get(), current.get());
                previous = current;
                current = getNextBalanceSnapshot(previous);
            }

            log.info("Reconciliation completed successfully in {}", stopwatch);
        } catch (ReconciliationException e) {
            status.set(e.getStatus());
            log.warn("Reconciliation completed unsuccessfully in {}: {}", stopwatch, e.getMessage());
        } catch (Exception e) {
            status.set(ReconciliationStatus.FAILURE_UNKNOWN);
            log.error("Reconciliation completed unsuccessfully in {}", stopwatch, e);
        } finally {
            previous.map(BalanceSnapshot::getInstant).ifPresent(reconciliationProperties::setStartDate);
        }
    }

    private void reconcile(BalanceSnapshot previous, BalanceSnapshot current) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        reconcileCryptoTransfers(previous, current);
        reconcileTokenTransfers(previous, current);

        String name = current.getAccountBalanceFile().getName();
        log.info("Reconciled balance file {} in {}", name, stopwatch);
    }

    private void reconcileCryptoTransfers(BalanceSnapshot previous, BalanceSnapshot current) {
        long fromTimestamp = previous.getTimestamp();
        long toTimestamp = current.getTimestamp();
        var transfersBalance = previous.getBalances();

        jdbcOperations.query(CRYPTO_TRANSFER_QUERY, rs -> {
            long accountId = rs.getLong(1);
            long balance = rs.getLong(2);
            transfersBalance.merge(accountId, balance, Math::addExact);
        }, fromTimestamp, toTimestamp);

        var difference = difference(transfersBalance, current.getBalances());
        if (!difference.areEqual()) {
            throw new ReconciliationException(FAILURE_CRYPTO_TRANSFERS, fromTimestamp, toTimestamp, difference);
        }
    }

    private void reconcileTokenTransfers(BalanceSnapshot previous, BalanceSnapshot current) {
        if (!reconciliationProperties.isToken()) {
            return;
        }

        long fromTimestamp = previous.getTimestamp();
        long toTimestamp = current.getTimestamp();
        var tokenBalances = previous.getTokenBalances();

        jdbcOperations.query(TOKEN_TRANSFER_QUERY, rs -> {
            long accountId = rs.getLong(1);
            long tokenId = rs.getLong(2);
            long balance = rs.getLong(3);
            var tokenAccountId = new TokenAccountId(accountId, tokenId);
            tokenBalances.merge(tokenAccountId, balance, Math::addExact);
        }, fromTimestamp, toTimestamp);

        var difference = difference(tokenBalances, current.getTokenBalances());
        if (!difference.areEqual()) {
            throw new ReconciliationException(FAILURE_TOKEN_TRANSFERS, fromTimestamp, toTimestamp, difference);
        }
    }

    private <T> MapDifference<Object, Long> difference(Map<T, Long> previous, Map<T, Long> current) {
        Sets.difference(previous.keySet(), current.keySet()).forEach(k -> current.put(k, 0L));
        Sets.difference(current.keySet(), previous.keySet()).forEach(k -> previous.put(k, 0L));
        return Maps.difference(previous, current);
    }

    private Optional<BalanceSnapshot> getNextBalanceSnapshot(Optional<BalanceSnapshot> previous) {

        long toTimestamp = DomainUtils.convertToNanosMax(reconciliationProperties.getEndDate());
        long fromTimestamp = previous.map(BalanceSnapshot::getAccountBalanceFile)
                .map(AccountBalanceFile::getConsensusTimestamp)
                .map(t -> t + 1L)
                .orElseGet(() -> DomainUtils.convertToNanosMax(reconciliationProperties.getStartDate()));

        return accountBalanceFileRepository.findNextInRange(fromTimestamp, toTimestamp)
                .map(accountBalanceFile -> {
                    var balances = getAccountBalances(accountBalanceFile);
                    var tokenBalances = getTokenBalances(accountBalanceFile);
                    return new BalanceSnapshot(accountBalanceFile, balances, tokenBalances);
                });
    }

    private Map<Long, Long> getAccountBalances(AccountBalanceFile accountBalanceFile) {
        Map<Long, Long> balances = new HashMap<>();
        AtomicLong total = new AtomicLong(0L);
        long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();

        jdbcOperations.query(BALANCE_QUERY, rs -> {
            long accountId = rs.getLong(1);
            long balance = rs.getLong(2);
            balances.put(accountId, balance);
            total.addAndGet(balance);
        }, consensusTimestamp);

        if (total.get() != FIFTY_BILLION_HBARS) {
            String name = accountBalanceFile.getName();
            throw new ReconciliationException(FAILURE_FIFTY_BILLION, name, total.get());
        }

        return balances;
    }

    private Map<TokenAccountId, Long> getTokenBalances(AccountBalanceFile accountBalanceFile) {
        Map<TokenAccountId, Long> balances = new HashMap<>();
        long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();

        jdbcOperations.query(TOKEN_BALANCE_QUERY, rs -> {
            long accountId = rs.getLong(1);
            long tokenId = rs.getLong(2);
            long balance = rs.getLong(3);
            var tokenAccountId = new TokenAccountId(accountId, tokenId);
            balances.put(tokenAccountId, balance);
        }, consensusTimestamp);

        return balances;
    }

    @Getter
    @RequiredArgsConstructor
    enum ReconciliationStatus {
        SUCCESS(""),
        FAILURE_CRYPTO_TRANSFERS("Crypto transfers in range (%d, %d]: %s"),
        FAILURE_FIFTY_BILLION("Balance file %s does not add up to 50B: %d"),
        FAILURE_TOKEN_TRANSFERS("Token transfers in range (%d, %d]: %s"),
        FAILURE_UNKNOWN("Unknown error");

        private final String message;
    }

    @Value
    static class TokenAccountId {
        private final long accountId;
        private final long tokenId;
    }

    @Value
    private class BalanceSnapshot {
        private final AccountBalanceFile accountBalanceFile;
        private final Map<Long, Long> balances;
        private final Map<TokenAccountId, Long> tokenBalances;

        private Instant getInstant() {
            return Instant.ofEpochSecond(0L, accountBalanceFile.getConsensusTimestamp());
        }

        private long getTimestamp() {
            return accountBalanceFile.getConsensusTimestamp() + accountBalanceFile.getTimeOffset();
        }
    }
}
