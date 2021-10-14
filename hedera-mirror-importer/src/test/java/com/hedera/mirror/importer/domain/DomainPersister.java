package com.hedera.mirror.importer.domain;

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

import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.repository.CrudRepository;

@Log4j2
@RequiredArgsConstructor
public class DomainPersister<T, B> {

    private final CrudRepository<T, ?> crudRepository;
    private final B builder;
    private final Supplier<T> supplier;

    public DomainPersister<T, B> customize(Consumer<B> customizer) {
        customizer.accept(builder);
        return this;
    }

    public T get() {
        return supplier.get();
    }

    public T persist() {
        T t = get();
        log.trace("Inserting {}", t);
        return crudRepository.save(t);
    }
}
