/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.graphql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.graphql.GraphqlIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRepositoryTest extends GraphqlIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void find() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void findByAlias() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity);
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity);
    }
}
