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

package com.hedera.mirror.importer.parser.record.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.util.CollectionUtils;

/**
 * Stores the domain objects parsed from the stream files before persisting to the database.
 */
public class ParserContext {

    private final Map<Class<?>, DomainContext<?>> state = new ConcurrentSkipListMap<>(new DomainClassComparator());

    public <T> void add(@NonNull T object) {
        var domainContext = getDomainContext(object);
        domainContext.getInserts().add(object);
    }

    public <T> void addAll(@NonNull Collection<T> objects) {
        if (!CollectionUtils.isEmpty(objects)) {
            var first = objects.iterator().next();
            var domainContext = getDomainContext(first);
            domainContext.getInserts().addAll(objects);
        }
    }

    public void drainTo(@NonNull Consumer<Collection<?>> sink) {
        state.forEach((c, v) -> sink.accept(v.getInserts()));
        state.clear();
    }

    public void drainTo(@NonNull Collection<Class<?>> domainClasses, @NonNull Consumer<Collection<?>> sink) {
        for (var domainClass : domainClasses) {
            var domainContext = getDomainContext(domainClass);
            sink.accept(domainContext.getInserts());
            domainContext.clear();
        }
    }

    public <T> T get(@NonNull Class<T> domainClass, @NonNull Object key) {
        var domainContext = getDomainContext(domainClass);
        return domainContext.getState().get(key);
    }

    public <T> void merge(@NonNull Object key, @NonNull T value, @NonNull BinaryOperator<T> mergeFunction) {
        var domainContext = getDomainContext(value);
        var merged = domainContext.getState().merge(key, value, mergeFunction);

        if (merged == value) {
            domainContext.getInserts().add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> DomainContext<T> getDomainContext(T object) {
        var domainClass = (Class<T>) object.getClass();
        return getDomainContext(domainClass);
    }

    @SuppressWarnings("unchecked")
    private <T> DomainContext<T> getDomainContext(Class<T> domainClass) {
        return (DomainContext<T>) state.computeIfAbsent(domainClass, c -> new DomainContext<>());
    }

    private class DomainContext<T> {

        @Getter
        private final Collection<T> inserts = new ArrayList<>();

        @Getter(lazy = true)
        private final Map<Object, T> state = new HashMap<>();

        void clear() {
            getInserts().clear();
            getState().clear();
        }
    }
}
