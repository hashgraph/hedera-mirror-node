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

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityHistoryRepositoryTest extends ImporterIntegrationTest {

    private final EntityHistoryRepository entityHistoryRepository;

    @Test
    void prune() {
        domainBuilder.entityHistory().persist();
        var entityHistory2 = domainBuilder.entityHistory().persist();
        var entityHistory3 = domainBuilder.entityHistory().persist();

        entityHistoryRepository.prune(entityHistory2.getTimestampUpper());

        assertThat(entityHistoryRepository.findAll()).containsExactly(entityHistory3);
    }

    @Test
    void save() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();
        assertThat(entityHistoryRepository.findById(entityHistory.getId()))
                .get()
                .isEqualTo(entityHistory);
    }

    @Test
    void updateContractType() {
        var entityHistory = domainBuilder.entityHistory().persist();
        var entityHistory2 = domainBuilder.entityHistory().persist();
        entityHistoryRepository.updateContractType(List.of(entityHistory.getId(), entityHistory2.getId()));
        assertThat(entityHistoryRepository.findAll())
                .hasSize(2)
                .extracting(EntityHistory::getType)
                .allMatch(e -> e == CONTRACT);
    }
}
