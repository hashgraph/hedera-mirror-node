/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityStakeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final EntityStakeHistoryRepository repository;

    @Test
    void prune() {
        domainBuilder.entityStakeHistory().persist();
        var entityStakeHistory2 = domainBuilder.entityStakeHistory().persist();
        var entityStakeHistory3 = domainBuilder.entityStakeHistory().persist();
        repository.prune(entityStakeHistory2.getTimestampUpper());
        assertThat(repository.findAll()).containsExactly(entityStakeHistory3);
    }

    @Test
    void save() {
        var entityStakeHistory = domainBuilder.entityStakeHistory().get();
        repository.save(entityStakeHistory);
        assertThat(repository.findAll()).containsExactly(entityStakeHistory);
    }
}
