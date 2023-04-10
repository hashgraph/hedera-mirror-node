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
import org.checkerframework.checker.units.qual.K;

/** One "level" of the stacked cache: cached entries for a particular state that can be thrown away
 * when you're done, or "committed" to the upstream frame to accumulate the latest values. Entities
 * of different types can be kept in this cache level, but all types kept must be registered on
 * creation of the level.
 *
 * @param <K> Key for all entries in the cache
 */
@SuppressWarnings(
        "java:S1192") // "define a constant instead of duplicating this literal" - worse readability if applied to small
// literals
public abstract class CachingStateFrame<K> {
    // ⮕ Nominations for a _better name_ than `StateFrame` are open ...

    protected final Optional<CachingStateFrame<K>> upstreamFrame;

    @NonNull
    protected final Map<Class<?>, AccessorImpl<?>> accessors;

    /** Create a new stacked cache level
     *
     * @param upstreamFrame upstream (fallback) frame to find entities in
     * @param klassesToCache array of the different types of the entities to manage here
     */
    protected CachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        requireAllNonNull(upstreamFrame, "upstreamFrame", klassesToCache, "klassesToCache");
        if (klassesToCache.length < 1)
            throw new IllegalArgumentException("must be caching for at least one value class");

        this.upstreamFrame = upstreamFrame;
        this.accessors = new HashMap<>(klassesToCache.length);
        Arrays.stream(klassesToCache).distinct().forEach(klass -> {
            Objects.requireNonNull(klass, "klassesToCache element");
            final var cache = new UpdatableReferenceCache<K>();
            accessors.put(klass, new AccessorImpl<>(klass, cache));
        });
    }

    /** Strongly-typed access to values stored in this `CachingStateFrame`.
     *
     * A `CachingStateFrame` can cache values of several _different_ types.  For type safety you want, as a caller
     * to make sure you are asking for and getting back values of the correct type, given the key.  Because of the
     * way that generics work in Java - through type erasure - there isn't enough information available at runtime
     * to validate this.  Thus, values are set and retrieved (and deleted) through these `Accessor`s.
     *
     * There's one per value type stored in this `CachingStateFrame`, and they're created at construction time, so it's
     * very cheap to get the `Accessor` for your value type and then use it (IOW, no `Accessor` is created and then
     * thrown away when you do that).
     */
    public interface Accessor<K, V> {

        /** Get a value from this `CachingStateFrame`, given its key, respecting stacked-cache behavior */
        Optional<V> get(@NonNull final K key);

        /** Set a value at a given key, in this `CachingStateFrame`, respecting stacked-cache behavior */
        void set(@NonNull final K key, @NonNull V value);

        /** Delete the value associated with this key from this `CachingStateFrame`, respecting stacked-cache behavior */
        void delete(@NonNull final K key);
    }

    /** Get the accessor for a kind of value that is managed by this cache. */
    @SuppressWarnings("unchecked")
    @NonNull
    <V> Accessor<K, V> getAccessor(@NonNull Class<V> klass) {
        Objects.requireNonNull(klass, "klass");

        final var accessor = (AccessorImpl<V>) accessors.get(klass);
        if (null != accessor) return accessor;
        throw new CacheAccessIncorrectType("%s values aren't cached here".formatted(klass.getName()));
    }

    /** Do the actual commit of entries from a descendant cache level to this one. */
    public abstract void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame);

    /** Cause new/updated/deleted entries to be persisted to the upstream level. */
    public void commit() {
        upstreamFrame.ifPresent(frame -> frame.updatesFromDownstream(this));
    }

    public @NonNull Optional<CachingStateFrame<K>> getUpstream() {
        return upstreamFrame;
    }

    public int height() {
        return upstreamFrame.map(pf -> 1 + pf.height()).orElse(1);
    }

    // Following are accessors to internal per-type caches - implemented by specific subclasses of `CachingStateFrame`.

    @NonNull
    protected abstract Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key);

    protected abstract void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value);

    protected abstract void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key);

    @NonNull
    protected Map<Class<?>, UpdatableReferenceCache<K>> getInternalCaches() {
        return accessors.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, kv -> kv.getValue().cache));
    }

    /** Enables type-safe access to one type of cached entities in this level of the cache.  (Accessor is _tied_
     * to this frame of the cache.) */
    @SuppressWarnings("java:S3655") // "Optional value should only be accessed after checking presence"
    //                              // - doesn't understand`flatMap`
    protected class AccessorImpl<V> implements Accessor<K, V> {

        @NonNull
        private final Class<V> klass;

        @NonNull
        private final UpdatableReferenceCache<K> cache;

        private AccessorImpl(@NonNull final Class<V> klass, @NonNull final UpdatableReferenceCache<K> cache) {
            requireAllNonNull(klass, "klass", cache, "cache");

            this.klass = klass;
            this.cache = cache;
        }

        /** Get an value from this level of the cache */
        @Override
        public Optional<V> get(@NonNull final K key) {
            Objects.requireNonNull(key, "key");
            final var oe = getValue(klass, cache, key);
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

        /** Set a new value for an value (in this level of the cache) */
        @Override
        public void set(@NonNull final K key, @NonNull V value) {
            requireAllNonNull(key, "key", value, "value");
            if (!klass.isInstance(value))
                throw new CacheAccessIncorrectType("trying to store %s in accessor for class %s"
                        .formatted(value.getClass().getTypeName(), klass.getTypeName()));
            setValue(klass, cache, key, value);
        }

        /** Delete an value (from in this level of the cache) */
        @Override
        public void delete(@NonNull final K key) {
            Objects.requireNonNull(key, "key");
            // Can't check type of value matches because we don't have one
            deleteValue(klass, cache, key);
        }
    }

    public static class CacheAccessIncorrectType extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 8163169205069277937L;

        public CacheAccessIncorrectType(@NonNull final String message) {
            super(message);
            Objects.requireNonNull(message);
        }

        public CacheAccessIncorrectType(@NonNull final String message, @NonNull final Throwable cause) {
            super(message, cause);
            requireAllNonNull(message, "message", cause, "cause");
        }
    }
}
