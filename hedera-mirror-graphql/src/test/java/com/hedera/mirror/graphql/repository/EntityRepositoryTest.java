package com.hedera.mirror.graphql.repository;

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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.FluentQuery;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.QEntity;
import com.hedera.mirror.graphql.GraphqlIntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRepositoryTest extends GraphqlIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void find() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Disabled("Not working")
    @Test
    void queryDsl() {
        var entity = domainBuilder.entity().persist();
        var spec = QEntity.entity.id.eq(entity.getId());
        assertThat(entityRepository.findBy(spec,
                (FluentQuery.FetchableFluentQuery<Entity> q) -> q.project("id", "memo").all()))
                .hasSize(1)
                .first()
                .returns(null, Entity::getPublicKey)
                .returns(entity.getId(), Entity::getId)
                .returns(entity.getMemo(), Entity::getMemo);
    }

    @Disabled("Dynamic projections in specification not supported. See spring-data-jpa#1524")
    @Test
    void specification() {
        var entity = domainBuilder.entity().persist();
        var spec = new Specification<Entity>() {

            @Override
            public Predicate toPredicate(Root<Entity> root, CriteriaQuery<?> query,
                                         CriteriaBuilder builder) {
                return query.multiselect(root.get("id"), root.get("memo"))
                        .where(builder.greaterThanOrEqualTo(root.get("id"), entity.getId()))
                        .getRestriction();
            }
        };
        assertThat(entityRepository.findAll(spec))
                .hasSize(1)
                .first()
                .returns(null, Entity::getPublicKey)
                .returns(entity.getId(), Entity::getId)
                .returns(entity.getMemo(), Entity::getMemo);
    }
}
