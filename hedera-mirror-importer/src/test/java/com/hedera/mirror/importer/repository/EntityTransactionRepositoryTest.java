/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityTransactionRepositoryTest extends AbstractRepositoryTest {

    private final EntityTransactionRepository repository;

    @Test
    void prune() {
        var entityTransaction1 = domainBuilder.entityTransaction().persist();
        var entityTransaction2 = domainBuilder.entityTransaction().persist();
        var entityTransaction3 = domainBuilder.entityTransaction().persist();

        repository.prune(entityTransaction1.getConsensusTimestamp());
        assertThat(repository.findAll()).containsExactlyInAnyOrder(entityTransaction2, entityTransaction3);

        repository.prune(entityTransaction2.getConsensusTimestamp());
        assertThat(repository.findAll()).containsExactly(entityTransaction3);
    }

    @Test
    void save() {
        var entityTransaction = domainBuilder.entityTransaction().get();
        repository.save(entityTransaction);
        assertThat(repository.findById(entityTransaction.getId())).contains(entityTransaction);
    }
}
