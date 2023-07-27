/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.83.1")
class FixAllowanceAmountsMigrationTest extends IntegrationTest {

    private static final EntityId PAYER = EntityId.of("0.0.1001", ACCOUNT);

    private static final String REVERT_DDL =
            """
                    alter table crypto_allowance
                        drop column created_timestamp,
                        drop column amount;
                    alter table crypto_allowance
                        rename column amount_granted to amount;
                    alter table crypto_allowance_history
                        drop column created_timestamp,
                        drop column amount;
                    alter table crypto_allowance_history
                        rename column amount_granted to amount;
                    alter table token_allowance
                        drop column created_timestamp,
                        drop column amount;
                    alter table token_allowance
                        rename column amount_granted to amount;
                    alter table token_allowance_history
                        drop column created_timestamp,
                        drop column amount;
                    alter table token_allowance_history
                        rename column amount_granted to amount;
                    """;

    private static final String PRE_TOKEN_ALLOWANCES =
            """
                    insert into token_allowance(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (100, 2, 1001, 5, '[4,)'::int8range, 1);
                    insert into token_allowance(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (500, 2, 1001, 6, '[5,)'::int8range, 2);
                    insert into token_allowance_history(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (10, 2, 1001, 5, '[1,2)'::int8range, 1);
                    insert into token_allowance_history(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (20, 2, 1001, 5, '[2,4)'::int8range, 1);
                    """;

    private static final String PRE_TOKEN_TRANSFERS =
            """
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -10, 2, true, 5, 1); -- older than current grant
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -10, 4, true, 5, 1);
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -40, 4, true, 5, 1);
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -50, 4, false, 5, 1); -- not approved
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -150, 6, true, 6, 2);
                    insert into token_transfer(account_id, amount, consensus_timestamp, is_approval, payer_account_id, token_id)
                    values (2, -200, 6, true, 22, 2); -- some other spender
                    """;

    @Owner
    private final JdbcOperations jdbcOperations;

    private final DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v1/V1.84.1__allowance_amount.sql")
    private final File migrationSql;

    private final TokenAllowanceRepository tokenAllowanceRepository;

    private final String idColumns = "payer_account_id, spender, token_id";

    @AfterEach
    void teardown() {
        runSql(REVERT_DDL);
    }

    @Test
    void empty() {
        migrate();

        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAllowance.class, idColumns)).isEmpty();
    }

    @Test
    void tokenMigrationNoTransfers() {
        runSql(PRE_TOKEN_ALLOWANCES);

        migrate();

        var tokenAllowanceBuilder = domainBuilder.tokenAllowance();

        var tokenAllowance1 = tokenAllowanceBuilder
                .customize(c -> {
                    // Migration simply sets amount == amountGranted when no approved transfers apply
                    c.amountGranted(100L).amount(100L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.timestampRange(Range.atLeast(4L));
                })
                .get();

        var tokenAllowance2 = tokenAllowanceBuilder
                .customize(c -> {
                    // Migration simply sets amount == amountGranted when no approved transfers apply
                    c.amountGranted(500L).amount(500L);
                    c.tokenId(2L).owner(2L).payerAccountId(PAYER).spender(6L);
                    c.timestampRange(Range.atLeast(5L));
                })
                .get();

        var tokenAllowanceHistory1 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(10L).amount(10L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.timestampRange(Range.closedOpen(1L, 2L));
                })
                .get();

        var tokenAllowanceHistory2 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(20L).amount(20L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.timestampRange(Range.closedOpen(2L, 4L));
                })
                .get();

        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
        assertThat(findHistory(TokenAllowance.class, idColumns))
                .containsExactlyInAnyOrder(tokenAllowanceHistory1, tokenAllowanceHistory2);
    }

    /*
     * Token transfers potentially affect the remaining amount of only allowance grants in token_allowances,
     * not in the history.
     */
    @Test
    void tokenMigrationWithTransfers() {
        runSql(PRE_TOKEN_ALLOWANCES);
        runSql(PRE_TOKEN_TRANSFERS);

        migrate();

        var tokenAllowanceBuilder = domainBuilder.tokenAllowance();

        var tokenAllowance1 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(100L).amount(50L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.timestampRange(Range.atLeast(4L));
                })
                .get();

        var tokenAllowance2 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(500L).amount(350L);
                    c.tokenId(2L).owner(2L).payerAccountId(PAYER).spender(6L);
                    c.timestampRange(Range.atLeast(5L));
                })
                .get();

        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
    }

    @SneakyThrows
    private void migrate() {
        runSql(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void runSql(String sql) {
        jdbcOperations.update(sql);
    }
}
