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

package com.hedera.mirror.web3.evm.store.accessor;

import java.util.Optional;
import lombok.NonNull;
import org.springframework.core.ResolvableType;

/** Interface for a database accessor to get some domain type V with primary key K from the database */
public abstract class DatabaseAccessor<K, V> {

    @SuppressWarnings("unchecked")
    protected DatabaseAccessor() {

        // Capture type parameter classes from impl class
        final var myKlass = getClass();
        final var implType = ResolvableType.forClass(DatabaseAccessor.class, myKlass);
        final var genericParameters = implType.getGenerics();
        klassKey = (Class<K>) genericParameters[0].toClass();
        klassValue = (Class<V>) genericParameters[1].toClass();
    }

    // Given address return an account record from the DB
    @NonNull
    public abstract Optional<V> get(@NonNull final K key);

    @NonNull
    public Class<K> getKeyClass() {
        return klassKey;
    }

    @NonNull
    public Class<V> getValueClass() {
        return klassValue;
    }

    private final Class<K> klassKey;
    private final Class<V> klassValue;
}
