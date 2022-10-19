package com.hedera.mirror.importer.migration;

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

import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.importer.MirrorProperties;

@Named
public class TokenAccountBalanceMigration extends RepeatableMigration {

    private static final int BATCH_SIZE = 100;

    private static final String ACCOUNT_BALANCE_SQL = "select consensus_timestamp from account_balance_file " +
            "order by consensus_timestamp desc limit 1";
    private static final String TOKEN_BALANCE_QUERY = "select account_id, token_id, balance from token_balance " +
            "where consensus_timestamp = ?";
    private static final String UPDATE_BALANCE_SQL = "update token_account set balance = ? " +
            "where account_id = ? and token_id = ?";

    private final JdbcOperations jdbcOperations;

    @Lazy
    public TokenAccountBalanceMigration(JdbcOperations jdbcOperations, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public String getDescription() {
        return "Initialize token account balance";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.67.1");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        var tokenAccounts = new ArrayList<TokenAccount>();

        var latestTimestamp = latestAccountBalanceFileTimestamp();
        if (latestTimestamp == null) {
            log.info("No account balance file found, skipping token account balance migration");
            return;
        }

        var tokenBalances = getTokenBalances(latestTimestamp);
        for (var tokenBalance : tokenBalances) {
            var id = new AbstractTokenAccount.Id();
            id.setAccountId(tokenBalance.getId().getAccountId().getId());
            id.setTokenId(tokenBalance.getId().getTokenId().getId());
            var tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(tokenBalance.getId().getAccountId().getId());
            tokenAccount.setTokenId(tokenBalance.getId().getTokenId().getId());
            tokenAccount.setBalance(tokenBalance.getBalance());
            tokenAccounts.add(tokenAccount);
        }

        jdbcOperations.batchUpdate(
                UPDATE_BALANCE_SQL,
                tokenAccounts,
                BATCH_SIZE,
                (ps, tokenAccount) -> {
                    ps.setLong(1, tokenAccount.getBalance());
                    ps.setLong(2, tokenAccount.getAccountId());
                    ps.setLong(3, tokenAccount.getTokenId());
                });
        log.info("Migrated {} token account balances in {}", tokenAccounts.size(), stopwatch);
    }

    private Long latestAccountBalanceFileTimestamp() {
        try {
            return jdbcOperations.queryForObject(ACCOUNT_BALANCE_SQL, Long.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private List<TokenBalance> getTokenBalances(long consensusTimestamp) {
        var tokenBalances = new ArrayList<TokenBalance>();
        jdbcOperations.query(TOKEN_BALANCE_QUERY, rs -> {
            long accountId = rs.getLong(1);
            long tokenId = rs.getLong(2);
            long balance = rs.getLong(3);
            var id = new TokenBalance.Id(consensusTimestamp, EntityId.of(accountId, EntityType.ACCOUNT),
                    EntityId.of(tokenId, EntityType.TOKEN));
            tokenBalances.add(new TokenBalance(balance, id));
        }, consensusTimestamp);

        return tokenBalances;
    }
}
