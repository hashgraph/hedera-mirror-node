package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.66.0")
class TokenAccountMigrationTest extends IntegrationTest {

    private static final String REVERT_SQL = """
            drop table if exists token_account;
            drop table if exists token_account_history;
            create table token_account (
              account_id            bigint not null,
              associated            boolean not null default false,
              automatic_association boolean not null default false,
              created_timestamp     bigint not null,
              freeze_status         smallint not null default 0,
              kyc_status            smallint not null default 0,
              modified_timestamp    bigint not null,
              token_id              bigint not null,
              primary key (account_id, token_id, modified_timestamp)
            );
            """;

    private final @Owner JdbcTemplate jdbcTemplate;
    @Value("classpath:db/migration/v1/V1.66.1__token_account_history.sql")
    private final File migrationSql;

    @AfterEach
    @SneakyThrows
    void teardown() {
        jdbcTemplate.execute(REVERT_SQL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(findAllTokenAccounts()).isEmpty();
        assertThat(findHistory().stream().count()).isZero();
    }

    @Test
    void migrate() {
        // given
        List<TokenAccountRange> expected = new ArrayList<>();
        List<TokenAccountRange> expectedHistory = new ArrayList<>();

        // token account relationships for account 100 and token 1000
        var last = MigrationTokenAccount.builder()
                .accountId(100)
                .associated(true)
                .createdTimestamp(1000)
                .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                .kycStatus(TokenKycStatusEnum.GRANTED)
                .modifiedTimestamp(1000)
                .tokenId(1000)
                .build();
        persistMigrationTokenAccount(last);
        List<Consumer<MigrationTokenAccount.MigrationTokenAccountBuilder>> customizers = List.of(
                b -> b.freezeStatus(TokenFreezeStatusEnum.FROZEN),
                b -> b.kycStatus(TokenKycStatusEnum.GRANTED),
                b -> b.associated(false),
                b -> b.associated(true).automaticAssociation(true).createdTimestamp(-1)
        );

        for (var customizer : customizers) {
            var next = update(last, customizer);
            expectedHistory.add(convert(last, next.getModifiedTimestamp()));
            last = next;
            persistMigrationTokenAccount(last);
        }
        expected.add(convert(last, null));

        // token account relationships for account 200 and token 2000
        last = MigrationTokenAccount.builder()
                .accountId(200)
                .associated(true)
                .createdTimestamp(2000)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .modifiedTimestamp(2000)
                .tokenId(2000)
                .build();
        persistMigrationTokenAccount(last);
        var next = update(last, b -> b.associated(false));
        persistMigrationTokenAccount(next);
        expectedHistory.add(convert(last, next.getModifiedTimestamp()));
        expected.add(convert(next, null));

        // when
        runMigration();

        // then
        assertThat(findAllTokenAccounts()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(findHistory()).map(t -> convert(t)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private TokenAccountRange convert(MigrationTokenAccount last, Long upperTimestamp) {
        Range<Long> range = upperTimestamp != null ? Range.closedOpen(last.getModifiedTimestamp(), upperTimestamp) :
                Range.atLeast(last.getModifiedTimestamp());
        return TokenAccountRange.builder()
                .accountId(last.getAccountId())
                .associated(last.isAssociated())
                .automaticAssociation(last.isAutomaticAssociation())
                .createdTimestamp(last.getCreatedTimestamp())
                .freezeStatus(last.getFreezeStatus())
                .kycStatus(last.getKycStatus())
                .timestampRange(range)
                .tokenId(last.getTokenId())
                .build();
    }

    private TokenAccountRange convert(TokenAccount tokenAccount) {
        return TokenAccountRange.builder()
                .accountId(tokenAccount.getAccountId())
                .associated(tokenAccount.getAssociated())
                .automaticAssociation(tokenAccount.getAutomaticAssociation())
                .createdTimestamp(tokenAccount.getCreatedTimestamp())
                .freezeStatus(tokenAccount.getFreezeStatus())
                .kycStatus(tokenAccount.getKycStatus())
                .timestampRange(tokenAccount.getTimestampRange())
                .tokenId(tokenAccount.getTokenId())
                .build();
    }

    private Collection<TokenAccount> findHistory() {
        return findHistory(TokenAccount.class, "account_id, token_id");
    }

    private void persistMigrationTokenAccount(MigrationTokenAccount migrationTokenAccount) {
        jdbcOperations.update("""
                        insert into token_account (account_id, associated, automatic_association, created_timestamp,
                            freeze_status, kyc_status, modified_timestamp, token_id)
                            values (?, ?, ?, ?, ?, ?, ? ,?)
                        """,
                migrationTokenAccount.getAccountId(),
                migrationTokenAccount.isAssociated(),
                migrationTokenAccount.isAutomaticAssociation(),
                migrationTokenAccount.getCreatedTimestamp(),
                migrationTokenAccount.getFreezeStatus().ordinal(),
                migrationTokenAccount.getKycStatus().ordinal(),
                migrationTokenAccount.getModifiedTimestamp(),
                migrationTokenAccount.getTokenId()
        );
    }

    @SneakyThrows
    private void runMigration() {
        jdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationTokenAccount update(MigrationTokenAccount current,
                                         Consumer<MigrationTokenAccount.MigrationTokenAccountBuilder> customizer) {
        var nextBuilder = current.toBuilder();
        customizer.accept(nextBuilder);
        var next = nextBuilder.build();
        next.setModifiedTimestamp(current.getModifiedTimestamp() + 100);
        if (next.getCreatedTimestamp() == -1) {
            // start a new token account relationship
            next.setCreatedTimestamp(next.getModifiedTimestamp());
        }
        return next;
    }

    @Builder(toBuilder = true)
    @Data
    private static class MigrationTokenAccount {
        private long accountId;
        private boolean associated;
        private boolean automaticAssociation;
        private long createdTimestamp;
        private long modifiedTimestamp;
        private TokenFreezeStatusEnum freezeStatus;
        private TokenKycStatusEnum kycStatus;
        private long tokenId;
    }

    @Builder(toBuilder = true)
    @Data
    private static class TokenAccountRange {
        private long accountId;
        private boolean associated;
        private boolean automaticAssociation;
        private long createdTimestamp;
        private TokenFreezeStatusEnum freezeStatus;
        private TokenKycStatusEnum kycStatus;
        private Range timestampRange;
        private long tokenId;
    }

    private List<TokenAccountRange> findAllTokenAccounts() {
        return jdbcTemplate.query("select " +
                        "account_id, " +
                        "associated, " +
                        "automatic_association, " +
                        "created_timestamp, " +
                        "freeze_status, " +
                        "kyc_status, " +
                        "lower(timestamp_range), " +
                        "token_id " +
                        "from token_account",
                (rs, index) ->
                        TokenAccountRange.builder()
                                .accountId(rs.getLong("account_id"))
                                .associated(rs.getBoolean("associated"))
                                .automaticAssociation(rs.getBoolean("automatic_association"))
                                .createdTimestamp(rs.getLong("created_timestamp"))
                                .freezeStatus(TokenFreezeStatusEnum.values()[rs.getInt("freeze_status")])
                                .kycStatus(TokenKycStatusEnum.values()[rs.getInt("kyc_status")])
                                .timestampRange(Range.atLeast(rs.getLong(7)))
                                .tokenId(rs.getLong("token_id"))
                                .build()
        );
    }
}
