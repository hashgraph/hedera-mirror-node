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

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.Query;

@Named
@RequiredArgsConstructor
public class JooqRepository {
    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public <T> List<T> getEntities(Query query, Class<T> type) {
        var nativeQuery = entityManager.createNativeQuery(query.getSQL(), type);
        var values = query.getBindValues();
        for (int i = 0; i < values.size(); i++) {
            nativeQuery.setParameter(i + 1, values.get(i));
        }

        return nativeQuery.getResultList();
    }

    public <T> Optional<T> getEntity(Query query, Class<T> type) {
        var entities = getEntities(query, type);
        return entities.isEmpty() ? Optional.empty() : Optional.of(entities.get(0));
    }
}
