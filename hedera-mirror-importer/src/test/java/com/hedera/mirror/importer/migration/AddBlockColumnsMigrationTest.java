/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.RecordFileMigrationTest;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.StreamUtils;

@ContextConfiguration(initializers = AddBlockColumnsMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
public class AddBlockColumnsMigrationTest extends RecordFileMigrationTest {

    private static final String REVERT_DDL =
            """
            alter table record_file
              drop column round_end,
              drop column round_start,
              drop column software_version_major,
              drop column software_version_minor,
              drop column software_version_patch;
            """;

    private final @Owner JdbcTemplate jdbcTemplate;
    private final RecordFileRepository recordFileRepository;

    @Value("${migrationSql}")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(recordFileRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given 3 record files all with unique hapi versions.
        var expectedRecordFiles = List.of(
                domainBuilder
                        .recordFile()
                        .customize(r -> r.hapiVersionMajor(1)
                                .softwareVersionMajor(1)
                                .roundEnd(null)
                                .roundStart(null))
                        .get(),
                domainBuilder
                        .recordFile()
                        .customize(r -> r.hapiVersionMinor(2)
                                .softwareVersionMinor(2)
                                .roundEnd(null)
                                .roundStart(null))
                        .get(),
                domainBuilder
                        .recordFile()
                        .customize(r -> r.hapiVersionPatch(3)
                                .softwareVersionPatch(3)
                                .roundEnd(null)
                                .roundStart(null))
                        .get());

        // software versions are not included when converted to MigrationRecordFiles
        expectedRecordFiles.forEach(this::persistRecordFile);

        // when
        runMigration();

        // then the software versions are updated to equal the hapi versions
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedRecordFiles);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            jdbcTemplate.execute(script);
        }
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version;
            String migrationSql;
            if (environment.acceptsProfiles(Profiles.of("v2"))) {
                version = "2.6.0";
                migrationSql = "classpath:db/migration/v2/V2.7.0__add_block_columns.sql";
            } else {
                version = "1.101.0";
                migrationSql = "classpath:db/migration/v1/V1.102.0__add_block_columns.sql";
            }

            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
            TestPropertyValues.of("migrationSql=" + migrationSql).applyTo(environment);
        }
    }
}
