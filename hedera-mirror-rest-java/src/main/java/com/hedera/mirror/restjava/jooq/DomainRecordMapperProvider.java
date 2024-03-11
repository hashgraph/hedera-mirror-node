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

package com.hedera.mirror.restjava.jooq;

import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.RecordMapperProvider;
import org.jooq.RecordType;
import org.jooq.impl.DefaultRecordMapper;

@Named
public class DomainRecordMapperProvider implements RecordMapperProvider {

    private static final String PACKAGE_PREFIX = "com.hedera.mirror";

    private final Map<MapperKey, RecordMapper<?, ?>> mappers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <R extends Record, E> RecordMapper<R, E> provide(
            RecordType<R> recordType, Class<? extends E> type) {
        var key = new MapperKey(recordType, type);
        return (RecordMapper<R, E>) mappers.computeIfAbsent(key, k -> {
            if (type.getName().startsWith(PACKAGE_PREFIX)) {
                return new DomainRecordMapper<>(recordType, type);
            }

            return new DefaultRecordMapper<>(recordType, type);
        });
    }

    private record MapperKey(RecordType<?> recordType, Class<?> type) {}
}
