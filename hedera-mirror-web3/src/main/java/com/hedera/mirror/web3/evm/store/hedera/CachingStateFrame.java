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
import java.io.Serial;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** One "level" of the stacked cache: cached entries for a particular state that can be thrown away
 * when you're done, or "committed" to the upstream frame to accumulate the latest values. Entities
 * of different types can be kept in this cache level, but all types kept must be registered on
 * creation of the level.
 *
 * @param <Address> Key for all entries in the cache
 */
public abstract class CachingStateFrame<Address> {
    // ⮕ Nominations for a _better name_ than `StateFrame` are open ...

    @NonNull
    protected final Optional<CachingStateFrame<Address>> upstreamFrame;

    @NonNull
    protected final Map<Class<?>, AccessorImpl<?>> accessors;

    /** Create a new stacked cache level
     *
     * @param upstreamFrame upstream (fallback) frame to find entities in
     * @param klassesToCache array of the different types of the entities to manage here
     */
    protected CachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address>> upstreamFrame,
            @NonNull final Class<?>... klassesToCache) {
        requireAllNonNull(upstreamFrame, "upstreamFrame", klassesToCache, "klassesToCache");

        this.upstreamFrame = upstreamFrame;
        this.accessors = new HashMap<>(klassesToCache.length);
        Arrays.stream(klassesToCache).distinct().forEach(klass -> {
            Objects.requireNonNull(klass, "klassesToCache element");
            final var cache = new UpdatableReferenceCache<Address>();
            accessors.put(klass, new AccessorImpl<>(klass, cache));
        });
    }

    public interface Accessor<Address, Entity> {
        Optional<Entity> get(@NonNull final Address address);

        void set(@NonNull final Address address, @NonNull Entity entity);

        void delete(@NonNull final Address address);
    }

    /** Get the accessor for a kind of entity that is managed by this cache. */
    @SuppressWarnings("unchecked")
    @NonNull
    <Entity> Accessor<Address, Entity> getAccessor(Class<Entity> klass) {
        try {
            return (AccessorImpl<Entity>) accessors.get(klass);
        } catch (final NullPointerException ex) {
            throw new CacheAccessIncorrectType("%s entities aren't cached here".formatted(klass.getName()), ex);
        }
    }

    /** Do the actual commit of entries from a descendant cache level to this one. */
    public abstract void updatesFromDownstream(@NonNull final CachingStateFrame<Address> childFrame);

    /** Cause new/updated/deleted entries to be persisted to the upstream level. */
    public void commit() {
        upstreamFrame.ifPresent(frame -> frame.updatesFromDownstream(this));
    }

    public @NonNull Optional<CachingStateFrame<Address>> getUpstream() {
        return upstreamFrame;
    }

    public int height() {
        return upstreamFrame.map(pf -> 1 + pf.height()).orElse(1);
    }

    public @NonNull Optional<CachingStateFrame<Address>> next() {
        return upstreamFrame;
    }

    // Following are accessors to internal per-type caches

    @NonNull
    protected abstract Optional<Object> getEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address);

    protected abstract void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity);

    protected abstract void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address);

    @NonNull
    protected Map<Class<?>, UpdatableReferenceCache<Address>> getInternalCaches() {
        return accessors.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, kv -> kv.getValue().cache));
    }

    /** Enables type-safe access to one type of cached entities in this level of the cache.  (Accessor is _tied_
     * to this frame of the cache.) */
    @SuppressWarnings("java:S3655") // "Optional value should only be accessed after checking presence"
    //                              // - doesn't understand`flatMap`
    public class AccessorImpl<Entity> implements Accessor<Address, Entity> {

        private final Class<Entity> klass;

        private final UpdatableReferenceCache<Address> cache;

        private AccessorImpl(
                @NonNull final Class<Entity> klass, @NonNull final UpdatableReferenceCache<Address> cache) {
            this.klass = klass;
            this.cache = cache;
        }

        /** Get an entity from this level of the cache */
        @Override
        public Optional<Entity> get(@NonNull final Address address) {
            Objects.requireNonNull(address, "address");
            final var oe = getEntity(klass, cache, address);
            try {
                return oe.flatMap(o -> Optional.of(klass.cast(o)));
            } catch (final ClassCastException ex) {
                throw new CacheAccessIncorrectType(
                        "accessor for class %s fetched object of class %s"
                                .formatted(
                                        klass.getTypeName(), oe.get().getClass().getTypeName()),
                        ex);
            }
        }

        /** Set a new value for an entity (in this level of the cache) */
        @Override
        public void set(@NonNull final Address address, @NonNull Entity entity) {
            requireAllNonNull(address, "address", entity, "entity");
            if (!klass.isInstance(entity))
                throw new CacheAccessIncorrectType("trying to store %s in accessor for class %s"
                        .formatted(entity.getClass().getTypeName(), klass.getTypeName()));
            setEntity(klass, cache, address, entity);
        }

        /** Delete an entity (from in this level of the cache) */
        @Override
        public void delete(@NonNull final Address address) {
            Objects.requireNonNull(address, "address");
            // Can't check type of entity matches because we don't have one
            deleteEntity(klass, cache, address);
        }
    }

    public static class CacheAccessIncorrectType extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 8163169205069277937L;

        public CacheAccessIncorrectType(@NonNull final String message) {
            super(message);
        }

        public CacheAccessIncorrectType(@NonNull final String message, @NonNull final Throwable cause) {
            super(message, cause);
        }
    }
}
