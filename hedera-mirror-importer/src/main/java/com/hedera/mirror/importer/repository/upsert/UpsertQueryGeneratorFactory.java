/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository.upsert;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
@RequiredArgsConstructor
public class UpsertQueryGeneratorFactory {

    private final EntityMetadataRegistry entityMetadataRegistry;
    private final Collection<UpsertQueryGenerator> existingGenerators;
    private final Map<Class<?>, UpsertQueryGenerator> upsertQueryGenerators = new ConcurrentHashMap<>();

    public UpsertQueryGenerator get(Class<?> domainClass) {
        return upsertQueryGenerators.computeIfAbsent(domainClass, this::findOrCreate);
    }

    /**
     * This method relies on the convention that the domain class and its associated UpsertQueryGenerator have the same
     * prefix. Otherwise, it falls back to creating a generic upsert query generator.
     */
    private UpsertQueryGenerator findOrCreate(Class<?> domainClass) {
        String className = domainClass.getSimpleName() + UpsertQueryGenerator.class.getSimpleName();
        return existingGenerators.stream()
                .filter(u -> u.getClass().getSimpleName().equals(className))
                .findFirst()
                .orElseGet(() -> create(domainClass));
    }

    private UpsertQueryGenerator create(Class<?> domainClass) {
        EntityMetadata entityMetadata = entityMetadataRegistry.lookup(domainClass);
        return new GenericUpsertQueryGenerator(entityMetadata);
    }
}
