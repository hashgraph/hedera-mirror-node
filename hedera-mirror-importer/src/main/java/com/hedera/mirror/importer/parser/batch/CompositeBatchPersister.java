package com.hedera.mirror.importer.parser.batch;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.collect.Iterables;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import javax.persistence.Entity;
import javax.sql.DataSource;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.util.AnnotatedTypeScanner;

import com.hedera.mirror.importer.domain.Upsertable;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGenerator;

@Named
@Primary
public class CompositeBatchPersister implements BatchPersister {

    private final Map<Class<?>, BatchPersister> batchInserters = new HashMap<>();

    public CompositeBatchPersister(DataSource dataSource,
                                   MeterRegistry meterRegistry,
                                   CommonParserProperties properties,
                                   Collection<UpsertQueryGenerator> upsertQueryGenerators) {
        AnnotatedTypeScanner annotatedTypeScanner = new AnnotatedTypeScanner(Entity.class);
        Set<Class<?>> domainClasses = annotatedTypeScanner.findTypes(Upsertable.class.getPackageName());

        for (Class<?> domainClass : domainClasses) {
            Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);
            BatchPersister batchPersister = null;

            if (upsertable != null) {
                UpsertQueryGenerator generator = getUpsertQueryGenerator(upsertQueryGenerators, domainClass);
                batchPersister = new BatchUpserter(domainClass, dataSource, meterRegistry, properties, generator);
            } else {
                batchPersister = new BatchInserter(domainClass, dataSource, meterRegistry, properties);
            }

            batchInserters.put(domainClass, batchPersister);
        }
    }

    @Override
    public void persist(Collection<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Object item = Iterables.getFirst(items, null);
        BatchPersister batchPersister = batchInserters.get(item.getClass());

        if (batchPersister == null) {
            throw new UnsupportedOperationException("Object does not support batch insertion: " + item.getClass());
        }

        batchPersister.persist(items);
    }

    /**
     * This method relies on the convention that the domain class and its associated UpsertQueryGenerator have the same
     * prefix.
     */
    private UpsertQueryGenerator getUpsertQueryGenerator(Collection<UpsertQueryGenerator> upsertQueryGenerators,
                                                         Class<?> domainClass) {
        String className = domainClass.getSimpleName() + UpsertQueryGenerator.class.getSimpleName();
        return upsertQueryGenerators.stream()
                .filter(u -> u.getClass().getSimpleName().equals(className))
                .findFirst()
                .orElseThrow();
    }
}
