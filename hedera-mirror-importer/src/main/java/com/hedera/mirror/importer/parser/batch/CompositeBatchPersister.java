package com.hedera.mirror.importer.parser.batch;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.persistence.Entity;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;

import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.UpsertQueryGeneratorFactory;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeBatchPersister implements BatchPersister {

    private final Map<Class<?>, BatchPersister> batchPersisters = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final CommonParserProperties properties;
    private final UpsertQueryGeneratorFactory upsertQueryGeneratorFactory;

    @Override
    public void persist(Collection<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Object item = items.iterator().next();
        if (item == null) {
            throw new UnsupportedOperationException("Object does not support batch insertion: " + item);
        }

        BatchPersister batchPersister = batchPersisters.computeIfAbsent(item.getClass(), this::create);
        batchPersister.persist(items);
    }

    private BatchPersister create(Class<?> domainClass) {
        Entity entity = AnnotationUtils.findAnnotation(domainClass, Entity.class);

        if (entity == null) {
            throw new UnsupportedOperationException("Object does not support batch insertion: " + domainClass);
        }

        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);
        if (upsertable != null) {
            UpsertQueryGenerator generator = upsertQueryGeneratorFactory.get(domainClass);
            return new BatchUpserter(domainClass, dataSource, meterRegistry, properties, generator);
        } else {
            return new BatchInserter(domainClass, dataSource, meterRegistry, properties);
        }
    }
}
