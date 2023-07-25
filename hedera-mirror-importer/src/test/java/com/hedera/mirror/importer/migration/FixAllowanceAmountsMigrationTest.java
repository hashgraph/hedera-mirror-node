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
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceHistoryRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
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

    private static final String PRE_TOKEN_ALLOWANCES =
            """
                    insert into token_allowance(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (100, 2, 1001, 5, '[4,)'::int8range, 1);
                    insert into token_allowance_history(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (10, 2, 1001, 5, '[1,2)'::int8range, 1);
                    insert into token_allowance_history(amount, owner, payer_account_id, spender, timestamp_range, token_id)
                    values (20, 2, 1001, 5, '[2,4)'::int8range, 1);
                    """;

    private static final String PRE_TOKEN_TRANSFERS =
            """
                    insert into token_transfer(amount, consensus_timestamp, entity_id, is_approval, payer_account_id)
                    values (-10, 4, 2, true, 5);
                    insert into token_transfer(amount, consensus_timestamp, entity_id, is_approval, payer_account_id)
                    values (-20, 5, 2, true, 5);
                    """;

    @Owner
    private final JdbcOperations jdbcOperations;

    private final DomainBuilder domainBuilder;

    @Value("classpath:db/migration/v1/V1.84.0__allowance_amount.sql")
    private final File migrationSql;

    private final EntityRepository entityRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenAllowanceHistoryRepository tokenAllowanceHistoryRepository;

    // @Test
    void empty() {
        migrate();

        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(tokenAllowanceHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void tokenMigrationNoTransfers() {
        final var idColumns = "owner, spender, token_id";

        runSql(PRE_TOKEN_ALLOWANCES);

        migrate();

        var tokenAllowanceBuilder = domainBuilder.tokenAllowance();

        var tokenAllowance = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(100L).amount(100L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.createdTimestamp(1L).timestampRange(Range.atLeast(4L));
                })
                .get();

        var tokenAllowanceHistory1 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(10L).amount(10L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.createdTimestamp(1L).timestampRange(Range.closedOpen(1L, 2L));
                })
                .get();

        var tokenAllowanceHistory2 = tokenAllowanceBuilder
                .customize(c -> {
                    c.amountGranted(20L).amount(20L);
                    c.tokenId(1L).owner(2L).payerAccountId(PAYER).spender(5L);
                    c.createdTimestamp(1L).timestampRange(Range.closedOpen(2L, 4L));
                })
                .get();

        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowance);
        assertThat(findHistory(TokenAllowance.class, idColumns))
                .containsExactlyInAnyOrder(tokenAllowanceHistory1, tokenAllowanceHistory2);
    }

    // Range.closedOpen(timestampLower, timestampUpper)
    @SneakyThrows
    private void migrate() {
        runSql(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void runSql(String sql) {
        jdbcOperations.update(sql);
    }
}
