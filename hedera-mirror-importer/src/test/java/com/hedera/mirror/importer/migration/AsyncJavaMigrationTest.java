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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.db.DBProperties;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class AsyncJavaMigrationTest extends IntegrationTest {

    private static final int ELAPSED = 20;
    private static final String TEST_MIGRATION_DESCRIPTION = "Async java migration for testing";

    private final DBProperties dbProperties;
    private final NamedParameterJdbcTemplate jdbcTemplate2;
    private final TransactionOperations transactionOperations;
    private final String script = TestAsyncJavaMigration.class.getName();

    @AfterEach
    @BeforeEach
    void cleanup() {
        jdbcTemplate2.update("delete from flyway_schema_history where script = :script", Map.of("script", script));
    }

    @ParameterizedTest
    @CsvSource(value = {
            ", -1",
            "-1, -2",
            "1, 1",
            "2, -1"
    })
    void getChecksum(Integer existing, Integer expected) {
        addMigrationHistory(new MigrationHistory(existing, ELAPSED, 1000));
        var migration = new TestAsyncJavaMigration(false, 0, 1);
        assertThat(migration.getChecksum()).isEqualTo(expected);
    }

    @Test
    void migrate() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        addMigrationHistory(new MigrationHistory(-2, ELAPSED, 1001));
        var migration = new TestAsyncJavaMigration(false, 0, 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(MigrationHistory::getChecksum)
                .containsExactly(-1, 1);
    }

    @Test
    void migrateUpdatedExecutionTime() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        var migration = new TestAsyncJavaMigration(false, 1, 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(1)
                .extracting(MigrationHistory::getExecutionTime)
                .isNotEqualTo(ELAPSED);
    }

    @Test
    void migrateError() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, ELAPSED, 1000));
        addMigrationHistory(new MigrationHistory(-2, ELAPSED, 1001));
        var migration = new TestAsyncJavaMigration(true, 0, 1);
        migrateSync(migration);
        assertThat(getAllMigrationHistory())
                .hasSize(2)
                .extracting(MigrationHistory::getChecksum)
                .containsExactly(-1, -2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void migrateNonPositiveSuccessChecksum(int checksum) {
        var migration = new TestAsyncJavaMigration(false, 0, checksum);
        assertThatThrownBy(migration::doMigrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(getAllMigrationHistory()).isEmpty();
    }

    private void addMigrationHistory(MigrationHistory migrationHistory) {
        if (migrationHistory.getChecksum() == null) {
            return;
        }

        var paramSource = new MapSqlParameterSource()
                .addValue("installedRank", migrationHistory.getInstalledRank())
                .addValue("description", TEST_MIGRATION_DESCRIPTION)
                .addValue("script", script)
                .addValue("checksum", migrationHistory.getChecksum());
        var sql = """
                insert into flyway_schema_history (installed_rank, description, type, script, checksum,
                installed_by, execution_time, success) values (:installedRank, :description, 'JDBC', :script,
                :checksum, 20, 100, true)
                """;
        jdbcTemplate2.update(sql, paramSource);
    }

    private List<MigrationHistory> getAllMigrationHistory() {
        return jdbcTemplate2.query("select installed_rank, checksum, execution_time from flyway_schema_history where " +
                "script = :script order by installed_rank asc", Map.of("script", script), (rs, rowNum) -> {
            Integer checksum = rs.getInt("checksum");
            int executionTime = rs.getInt("execution_time");
            int installedRank = rs.getInt("installed_rank");
            return new MigrationHistory(checksum, executionTime, installedRank);
        });
    }

    private void migrateSync(AsyncJavaMigration migration) throws Exception {
        migration.doMigrate();

        while (!migration.isComplete()) {
            Uninterruptibles.sleepUninterruptibly(100L, TimeUnit.MILLISECONDS);
        }
    }

    @Value
    private static class MigrationHistory {
        private Integer checksum;
        private int executionTime;
        private int installedRank;
    }

    @Value
    private class TestAsyncJavaMigration extends AsyncJavaMigration<Long> {

        private final boolean error;
        private final long sleep;
        private final int successChecksum;

        public TestAsyncJavaMigration(boolean error, long sleep, int successChecksum) {
            super(jdbcTemplate2, dbProperties.getSchema(), transactionOperations);
            this.error = error;
            this.sleep = sleep;
            this.successChecksum = successChecksum;
        }

        @Override
        public String getDescription() {
            return TEST_MIGRATION_DESCRIPTION;
        }

        @Nonnull
        @Override
        protected Optional<Long> migratePartial(final Long last) {
            if (sleep > 0) {
                Uninterruptibles.sleepUninterruptibly(sleep, TimeUnit.SECONDS);
            }

            if (error) {
                throw new RuntimeException();
            }

            return Optional.empty();
        }

        @Override
        protected Long getInitial() {
            return Long.MAX_VALUE;
        }
    }
}
