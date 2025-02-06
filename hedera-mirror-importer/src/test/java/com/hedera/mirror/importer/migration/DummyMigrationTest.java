/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.ImporterProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;

@RequiredArgsConstructor
@Tag("migration")
class DummyMigrationTest extends ImporterIntegrationTest {

    private final ImporterProperties importerProperties;

    @Test
    void checksum() {
        var dummyMigration = new DummyMigration(importerProperties);
        assertThat(dummyMigration.getChecksum()).isEqualTo(5);
    }

    @Test
    void verifyPermissions() {
        final var sql =
                """
                create table if not exists test (id bigint primary key);
                insert into test (id) values (1);
                drop table test
                """;
        assertThatThrownBy(() -> jdbcOperations.update(sql)).isInstanceOf(DataAccessException.class);
        ownerJdbcTemplate.update(sql); // Succeeds
    }

    static class DummyMigration extends RepeatableMigration {

        @Getter
        private boolean migrated = false;

        @Lazy
        public DummyMigration(ImporterProperties importerProperties) {
            super(importerProperties.getMigration());
        }

        @Override
        protected void doMigrate() {
            migrated = true;
        }

        @Override
        public String getDescription() {
            return "Dummy migration";
        }
    }
}
