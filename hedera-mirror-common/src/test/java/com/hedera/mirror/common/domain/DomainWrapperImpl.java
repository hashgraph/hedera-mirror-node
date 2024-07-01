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

package com.hedera.mirror.common.domain;

import jakarta.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionOperations;

public record DomainWrapperImpl<T, B>(B builder, Supplier<T> supplier, EntityManager entityManager,
                                      TransactionOperations transactionOperations) implements DomainWrapper<T, B> {

    @Override
    public DomainWrapper<T, B> customize(Consumer<B> customizer) {
        customizer.accept(builder);
        return this;
    }

    @Override
    public T get() {
        return supplier.get();
    }

    // The DomainWrapper can be used without an active ApplicationContext. If so, this method shouldn't be used.
    @Override
    public T persist() {
        if (entityManager == null) {
            throw new IllegalStateException("Unable to persist entity without an EntityManager");
        }
        if (transactionOperations == null) {
            throw new IllegalStateException("Unable to persist entity without a TransactionOperations");
        }

        T entity = get();
        transactionOperations.executeWithoutResult(t -> entityManager.persist(entity));
        return entity;
    }
}
