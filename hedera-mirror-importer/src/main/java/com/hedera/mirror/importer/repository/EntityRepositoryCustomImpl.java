package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.hedera.mirror.importer.domain.EntityId;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

public class EntityRepositoryCustomImpl implements EntityRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    @Lazy
    EntityRepository entityRepository;

    public Collection<EntityId> findAllEntityIds(int limit) {
        var results = entityManager.createQuery("select new com.hedera.mirror.importer.domain.EntityId(id, entityShard, entityRealm, entityNum, entityTypeId) from Entities")
                .setMaxResults(limit)
                .getResultList();
        results.forEach(entityId -> {
            entityRepository.cache((EntityId)entityId);
        });
        return results;
    }

    /**
     * @param entityId for which the id needs to be looked up (from cache/repo). If no id is found, the the entity is
     *                 inserted into the repo and the newly minted id is returned.
     * @return looked up/newly minted id of the given entityId.
     */
    public Long lookupOrCreateId(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        Long id = entityId.getId();
        if (id != null && id != 0) {
            return id;
        }
        return entityRepository.findEntityIdByNativeIds(
                entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum())
                .orElseGet(() -> {
                    var entity = entityRepository.save(entityId.toEntity());
                    entityRepository.cache(entity.toEntityId()); // save to big cache
                    return entity.getId();
                });
    }
}
