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

package com.hedera.mirror.web3.evm.store;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import java.util.Optional;
import lombok.NonNull;

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and disallows updates/deletes. */
public class ROCachingStateFrame<K> extends CachingStateFrame<K> {

    private final Optional<EntityDatabaseAccessor> entityDatabaseAccessor;

    public ROCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame,
            final Optional<EntityDatabaseAccessor> entityDatabaseAccessor,
            @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
        this.entityDatabaseAccessor = entityDatabaseAccessor;
    }

    @Override
    @NonNull
    public Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        final var entry = cache.get(key);
        return switch (entry.state()) {
            case NOT_YET_FETCHED -> upstreamFrame.flatMap(upstreamFrame -> {
                final var upstreamAccessor = upstreamFrame.getAccessor(klass);
                final var upstreamValue = upstreamAccessor.get(key);

                // We need to make sure that we don't cache null value for a key when
                // the key belongs to an entity type different than the accessor type we want to use
                if (entityDatabaseAccessor.isPresent()) {
                    if (upstreamAccessor instanceof DatabaseAccessor<?, ?>) {
                        final var entity = entityDatabaseAccessor.get().get(key);
                        if (entity.isEmpty()) {
                            cache.fill(key, upstreamValue.orElse(null));
                        } else {
                            final var entityType = entity.get().getType();

                            if (EntityType.ACCOUNT.equals(entityType)
                                            && upstreamAccessor instanceof AccountDatabaseAccessor
                                    || EntityType.TOKEN.equals(entityType)
                                            && upstreamAccessor instanceof TokenDatabaseAccessor) {
                                cache.fill(key, upstreamValue.orElse(null));
                            }
                        }
                    }
                } else {
                    cache.fill(key, upstreamValue.orElse(null));
                }

                return upstreamValue;
            });
            case PRESENT, UPDATED -> Optional.of(entry.value());
            case MISSING, DELETED -> Optional.empty();
            case INVALID -> throw new IllegalArgumentException("Trying to get value when state is invalid");
        };
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        throw new UnsupportedOperationException("Cannot write value to a R/O cache");
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        throw new UnsupportedOperationException("Cannot delete value from a R/O cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
        throw new UnsupportedOperationException("Cannot commit to a R/O cache");
    }
}
