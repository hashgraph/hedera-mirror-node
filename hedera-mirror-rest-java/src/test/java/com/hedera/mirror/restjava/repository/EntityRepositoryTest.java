/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
 class EntityRepositoryTest extends RestJavaIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void exists() {
        var entity = domainBuilder.entity().persist();
        var entityDeleted =
                domainBuilder.entity().customize((b) -> b.deleted(true)).persist();
        var entityDeletedNull =
                domainBuilder.entity().customize((b) -> b.deleted(null)).persist();

        assertThat(entityRepository.existsById(entity.getId())).isTrue();
        assertThat(entityRepository.existsById(entityDeleted.getId())).isFalse();
        assertThat(entityRepository.existsById(entityDeletedNull.getId())).isFalse();
    }

    @Test
    void findByAlias() {
        var entity = domainBuilder.entity().persist();
        byte[] alias = entity.getAlias();
        var entityDeleted =
                domainBuilder.entity().customize((b) -> b.deleted(true)).persist();
        var entityDeletedNull =
                domainBuilder.entity().customize((b) -> b.deleted(null)).persist();

        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByAlias(entityDeleted.getAlias())).isEmpty();
        assertThat(entityRepository.findByAlias(entityDeletedNull.getAlias())).isEmpty();
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        var entityDeleted =
                domainBuilder.entity().customize((b) -> b.deleted(true)).persist();
        var entityDeletedNull =
                domainBuilder.entity().customize((b) -> b.deleted(null)).persist();

        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity.getId());
        assertThat(entityRepository.findByEvmAddress(entityDeleted.getEvmAddress()))
                .isEmpty();
        assertThat(entityRepository.findByEvmAddress(new byte[] {1, 2, 3})).isEmpty();
        assertThat(entityRepository.findByEvmAddress(entityDeletedNull.getEvmAddress()))
                .isEmpty();
    }
}
