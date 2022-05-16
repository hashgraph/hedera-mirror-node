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

import javax.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@EnabledIfV1
@Tag("migration")
public class BackfillBlockMigrationTest extends IntegrationTest {

    @Resource
    private BackfillBlockMigration backfillBlockMigration;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Test
    void empty() {
        backfillBlockMigration.migrateAsync();
        assertThat(recordFileRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {

    }
}
