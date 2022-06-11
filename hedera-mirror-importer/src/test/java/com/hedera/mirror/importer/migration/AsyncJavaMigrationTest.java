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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
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

    private static final String TEST_MIGRATION_DESCRIPTION = "Async java migration for testing";

    private final DBProperties dbProperties;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final TransactionOperations transactionOperations;

    private final String script = TestAsyncJavaMigration.class.getName();

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from flyway_schema_history where script = :script", Map.of("script", script));
    }

    @ParameterizedTest
    @CsvSource(value = {
            ", -1",
            "-1, -2",
            "1, 1",
            "2, -1"
    })
    void getChecksum(Integer existing, Integer expected) {
        addMigrationHistory(new MigrationHistory(existing, 1000));
        var migration = new TestAsyncJavaMigration(dbProperties, false, jdbcTemplate, 1, transactionOperations);
        assertThat(migration.getChecksum()).isEqualTo(expected);
    }

    @Test
    void migrate() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, 1000));
        addMigrationHistory(new MigrationHistory(-2, 1001));
        var migration = new TestAsyncJavaMigration(dbProperties, false, jdbcTemplate, 1, transactionOperations);
        migration.doMigrate();
        Thread.sleep(500);
        assertThat(getAllMigrationHistory()).containsExactlyInAnyOrder(new MigrationHistory(-1, 1000),
                new MigrationHistory(1, 1001));
    }

    @Test
    void migrateError() throws Exception {
        addMigrationHistory(new MigrationHistory(-1, 1000));
        addMigrationHistory(new MigrationHistory(-2, 1001));
        var migration = new TestAsyncJavaMigration(dbProperties, true, jdbcTemplate, 1, transactionOperations);
        migration.doMigrate();
        Thread.sleep(500);
        assertThat(getAllMigrationHistory()).containsExactlyInAnyOrder(new MigrationHistory(-1, 1000),
                new MigrationHistory(-2, 1001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void migrateNonPositiveSuccessChecksum(Integer checksum) throws Exception {
        var migration = new TestAsyncJavaMigration(dbProperties, false, jdbcTemplate, checksum, transactionOperations);
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
        jdbcTemplate.update("insert into flyway_schema_history (installed_rank, description, type, script, checksum, " +
                "installed_by, execution_time, success) values (:installedRank, :description, 'JDBC', :script, " +
                ":checksum, 20, 100, true)", paramSource);
    }

    private List<MigrationHistory> getAllMigrationHistory() {
        return jdbcTemplate.query("select installed_rank, checksum from flyway_schema_history where " +
                "script = :script", Map.of("script", script), (rs, rowNum) -> {
            Integer checksum = rs.getInt("checksum");
            int installedRank = rs.getInt("installed_rank");
            return new MigrationHistory(checksum, installedRank);
        });
    }

    @Value
    private static class MigrationHistory {
        private Integer checksum;
        private int installedRank;
    }

    @Value
    private static class TestAsyncJavaMigration extends AsyncJavaMigration<Long> {
        private final boolean error;
        private final int successChecksum;

        public TestAsyncJavaMigration(DBProperties dbProperties, boolean error, NamedParameterJdbcTemplate jdbcTemplate,
                                      int successChecksum, TransactionOperations transactionOperations) {
            super(jdbcTemplate, dbProperties.getSchema(), transactionOperations);
            this.error = error;
            this.successChecksum = successChecksum;
        }

        @Override
        public String getDescription() {
            return TEST_MIGRATION_DESCRIPTION;
        }

        @Nonnull
        @Override
        protected Optional<Long> migratePartial(final Long last) {
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
