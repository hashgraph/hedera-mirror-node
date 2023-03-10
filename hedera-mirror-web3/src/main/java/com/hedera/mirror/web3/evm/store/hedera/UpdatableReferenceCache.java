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

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.checkerframework.checker.units.qual.K;

/**
 * Runs a cache for references but treated as values, for use as part of a
 * _stacked_ cache with _commit_/_rollback_ to lower levels.
 *
 * Keeps track of current value of k/v pair, including if the key has been
 * deleted.  **Does not permit** a value to be "updated" because if you
 * actually _updated_ a reference associated with a key you'd _also_ be
 * changing that same reference in a lower-level cache, which would break
 * the intent of being able to commit/rollback.  Checked at runtime.  (But
 * prefer using an immutable type for `V` so this problem simply can't happen.)
 *
 * @param <K> key type
 * @param <V> value type
 */
public class UpdatableReferenceCache<K, V> {

    @NonNull
    protected final Class<V> klassV;

    @NonNull
    protected final Map<K, V> original = new HashMap<>(); // "missing" denoted by null values here

    @NonNull
    protected final Map<K, V> addDel = new HashMap<>(); // "deleted" denoted by null values here

    /**
     * Create an `UpdateableReferenceCache` for holding the cached value of some type.
     *
     * @param klassV - used to provide run-time type safety (due to type-erasure the class needs to be explicitly passed
     *              in to this cache, otherwise it doesn't know exactly what it's supposed to hold)
     */
    UpdatableReferenceCache(@NonNull final Class<V> klassV) {
        this.klassV = klassV;
    }

    /** Entities (denoted by their key) can be in one of the following states */
    public enum EntityState {
        NOT_YET_FETCHED, // haven't yet tried to read it from upstream cache
        PRESENT, // read from upstream cache, found
        MISSING, // read from upstream cache, wasn't present there
        UPDATED, // has been set (written) in this cache
        DELETED // has been deleted in this cache
    }

    public record Entry<V>(EntityState state, V value) {}

    public final Entry<V> valueNotYetFetchedMarker = new Entry<>(EntityState.NOT_YET_FETCHED, null);
    public final Entry<V> valueMissingMarker = new Entry<>(EntityState.MISSING, null);
    public final Entry<V> valueDeletedMarker = new Entry<>(EntityState.DELETED, null);

    // Get the current state/value of the key in this cache
    protected Entry<V> getCurrentState(@NonNull final K key) {
        Objects.requireNonNull(key, "key");

        Consumer<V> checkValueClass = value -> {
            if (null != value && value.getClass() != klassV)
                throw new IllegalStateException("caller is trying to get a %s but this is a %s cache"
                        .formatted(klassV.getTypeName(), value.getClass().getTypeName()));
        };

        // Java's `Map` doesn't let you find out in a single call if the value was in the map but `null` or not in the
        // map. So if the value turns out to be null you've got to do a second call to distinguish that.

        var value = addDel.get(key);
        checkValueClass.accept(value);
        if (null != value) return new Entry<>(EntityState.UPDATED, value);
        if (addDel.containsKey(key)) return valueDeletedMarker;

        value = original.get(key);
        checkValueClass.accept(value);
        if (null != value) return new Entry<>(EntityState.PRESENT, value);
        if (original.containsKey(key)) return valueMissingMarker;

        return valueNotYetFetchedMarker;
    }

    /**
     * Get from the cache
     */
    public Entry<V> get(@NonNull final K key) {
        Objects.requireNonNull(key, "key");
        return getCurrentState(key);
    }

    /**
     * Fill cache with a read from a lower level - used only in response to NOT_YET_FETCHED.
     */
    public void fill(@NonNull final K key, final @Nullable V value) {
        Objects.requireNonNull(key, "key");
        switch (getCurrentState(key).state()) {
            case NOT_YET_FETCHED -> original.put(key, value);
            case MISSING, PRESENT -> throw new IllegalArgumentException("trying to override a lower-level entry");
            case UPDATED, DELETED -> throw new IllegalArgumentException("trying to override an updated entry");
        }
    }

    /**
     * Put a new k/v or update an existing k/v, but _disallows_ attempt to _update_
     * an existing k/v with _the same value_ - because that would indicate that you
     * updated the reference value breaking the commit/rollback ability of this
     * (component of a) stacked cache.
     */
    @SuppressWarnings({"fallthrough", "java:S1301"
    }) // "replace this `switch` by an `if` to improve readability" - no: wrong advice here
    public void update(@NonNull final K key, @NonNull final V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (value.getClass() != klassV)
            throw new IllegalStateException("trying to update a %s cache with a %s"
                    .formatted(klassV.getTypeName(), value.getClass().getTypeName()));
        final var currentState = getCurrentState(key);
        switch (currentState.state()) {
            case PRESENT:
                if (currentState.value() == value) {
                    throw new IllegalArgumentException(
                            """
                            trying to update %s with exact same reference - probably \
                            indicates an error where the %s was modified"""
                                    .formatted(klassV.getTypeName(), klassV.getTypeName()));
                }
                // fallthrough
            case NOT_YET_FETCHED, MISSING, UPDATED, DELETED:
                addDel.put(key, value);
        }
    }

    /**
     * Delete from the cache - meaning that this key is considered _deleted_, not
     * a cache miss.
     */
    public void delete(@NonNull final K key) {
        Objects.requireNonNull(key, "key");
        switch (getCurrentState(key).state()) {
            case PRESENT, UPDATED -> addDel.put(key, null);
            case NOT_YET_FETCHED -> throw new IllegalArgumentException(
                    "trying to delete a %s that hasn't been fetched".formatted(klassV.getTypeName()));
            case MISSING, DELETED -> throw new IllegalArgumentException(
                    "trying to delete a missing/already deleted %s".formatted(klassV.getTypeName()));
        }
    }

    /**
     * This is the implementation of `commit`: merge another cache's updated
     * entries over ours.  Don't need to merge the originals because the intent of
     * this class is to support a stacked cache and the lower layers of the stack
     * (such as this one, being coalesced _into_) already have those entries.
     */
    public void coalesceFrom(@NonNull final UpdatableReferenceCache<K, V> source) {
        Objects.requireNonNull(source, "source");
        addDel.putAll(source.addDel);
    }

    @VisibleForTesting
    public record Counts(int read, int updated, int deleted) {
        public static Counts of(int r, int u, int d) {
            return new Counts(r, u, d);
        }
    }

    /**
     * Returns (for tests) a triple of #original accounts, #added accounts, #deleted accounts
     */
    @VisibleForTesting
    public @NonNull Counts getCounts() {
        return Counts.of(
                original.size(),
                (int) addDel.values().stream().filter(Objects::nonNull).count(),
                (int) addDel.values().stream().filter(Objects::isNull).count());
    }

    public enum Type {
        READ_ONLY,
        UPDATED,
        CURRENT_STATE
    }

    @VisibleForTesting
    public @NonNull Map<K, V> getAccounts(@NonNull final Type type) {
        Objects.requireNonNull(type, "type");
        // n.b.: can't use `Map.copyOf` because immutable maps created by `Map.copyOf` can't have values which are null.
        return switch (type) {
            case READ_ONLY -> new HashMap<>(original);
            case UPDATED -> new HashMap<>(addDel);
            case CURRENT_STATE -> {
                final var r = new HashMap<>(original);
                r.putAll(addDel); // why is `StringBuilder.append` fluid but `Map.putAll` not?
                yield r;
            }
        };
    }
}
