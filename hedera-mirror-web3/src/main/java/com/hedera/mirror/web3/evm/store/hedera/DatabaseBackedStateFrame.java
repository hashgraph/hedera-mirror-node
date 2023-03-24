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

package com.hedera.mirror.web3.evm.store.hedera;

import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** A CachingStateFrame that answers reads by getting entities from sone other source - a database! - and
 * disallows all local updates/deletes. */
@SuppressWarnings("java:S1192") // "string literals should not be duplicated"
public class DatabaseBackedStateFrame<Address> extends CachingStateFrame<Address> {

    @NonNull
    final Map<Class<?>, GroundTruthAccessor<Address, ?>> databaseAccessors;

    public DatabaseBackedStateFrame(
            @NonNull final List<GroundTruthAccessor<Address, ?>> accessors, @NonNull final Class<?>[] entityClasses) {
        super(
                Optional.empty(),
                entityClasses); // superclass of this frame will create/hold useless UpdatableReferenceCaches

        databaseAccessors = accessors.stream().collect(Collectors.toMap(GroundTruthAccessor::getVClass, a -> a));
    }

    @Override
    @NonNull
    public Optional<Object> getEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");

        return databaseAccessors.get(klass).get(address).flatMap(o -> Optional.of(klass.cast(o)));
    }

    @Override
    public void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address", entity, "entity");
        throw new UnsupportedOperationException("cannot add/update an entity in a database-backed StateFrame");
    }

    @Override
    public void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        throw new UnsupportedOperationException("cannot delete an entity in a database-backed StateFrame");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<Address> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a database-backed StateFrame (oddly enough)");
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException("cannot commit to a database-backed StateFrame (oddly enough)");
    }
}
