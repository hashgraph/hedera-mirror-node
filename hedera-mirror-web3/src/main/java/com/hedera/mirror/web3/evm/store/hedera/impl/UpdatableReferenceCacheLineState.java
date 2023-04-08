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

package com.hedera.mirror.web3.evm.store.hedera.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;

/** Utility class to compute the state of a cache line.
 *
 * Determines, from the two maps held by an UpdatableReferenceCache - original and current - the state of the
 * cache line.  The state is enumerated by ValueState.
 *
 * Not a true Java "utility" class because it can't hold only static methods.  Because it is generic in the type
 * of the cache key and one method uses that type parameter in its signature.
 *
 * @param <K> cache key type
 */
public class UpdatableReferenceCacheLineState<K> {

    /** Entities (denoted by their key) can be in one of the following states */
    public enum ValueState {
        INVALID, // invalid state
        NOT_YET_FETCHED, // haven't yet tried to read it from upstream cache
        PRESENT, // read from upstream cache, found
        MISSING, // read from upstream cache, wasn't present there
        UPDATED, // has been set (written) in this cache
        DELETED // has been deleted in this cache
    }

    public record Entry(ValueState state, Object value) {}

    private static final Entry invalidStateMarker = new Entry(ValueState.INVALID, null);
    private static final Entry valueNotYetFetchedMarker = new Entry(ValueState.NOT_YET_FETCHED, null);
    private static final Entry valueMissingMarker = new Entry(ValueState.MISSING, null);
    private static final Entry valueDeletedMarker = new Entry(ValueState.DELETED, null);

    /** Get the current state/value of the key in this cache - N.B.: This is _not an accurate state!_ It checks
     * adddel first and if it's there it does not go ahead and check original! Disambiguation is to be handled by
     * the caller! */
    public Entry get(
            @NonNull final Map<K, Object> original, @NonNull final Map<K, Object> current, @NonNull final K key) {
        Objects.requireNonNull(key, "key");

        final var valueO = original.get(key);
        final var kindO = determineKind(original, key, valueO).rank();
        final var valueC = current.get(key);
        final var kindC = determineKind(current, key, valueC).rank();

        final var state = toValueState[kindO][kindC];
        final var actualValue = toValue.get(kindO).get(kindC).apply(valueO, valueC);

        return switch (state) {
            case INVALID -> invalidStateMarker;
            case NOT_YET_FETCHED -> valueNotYetFetchedMarker;
            case MISSING -> valueMissingMarker;
            case DELETED -> valueDeletedMarker;
            case PRESENT, UPDATED -> new Entry(state, actualValue);
        };
    }

    private enum Kind {
        MISSING(0),
        NULL(1),
        NON_NULL(2);

        private final int rank;

        Kind(final int rank) {
            this.rank = rank;
        }

        int rank() {
            return rank;
        }
    }

    private Kind determineKind(@NonNull final Map<K, Object> map, @NonNull final K key, @Nullable final Object value) {
        // Java's `Map` doesn't let you find out in a single call if the value was in the map but `null` or not in the
        // map. So if the value turns out to be null you've got to do a second call to distinguish that.
        if (null != value) return Kind.NON_NULL;
        if (map.containsKey(key)) return Kind.NULL;
        return Kind.MISSING;
    }

    private static final ValueState[ /*original*/][ /*adddel*/] toValueState = {
        /*MISSING*/ {ValueState.NOT_YET_FETCHED, ValueState.INVALID, ValueState.UPDATED},
        /*NULL*/ {ValueState.MISSING, ValueState.INVALID, ValueState.UPDATED},
        /*NON_NULL*/ {ValueState.PRESENT, ValueState.DELETED, ValueState.UPDATED}
    };

    private static final BinaryOperator<Object> fromNull = (o, c) -> null;
    private static final BinaryOperator<Object> fromO = (o, d) -> o;
    private static final BinaryOperator<Object> fromAD = (o, c) -> c;

    private static final List<List<BinaryOperator<Object>>> toValue = List.of(
            /*MISSING*/ List.of(fromNull, fromNull, fromAD),
            /*NULL*/ List.of(fromNull, fromNull, fromAD),
            /*NON_NULL*/ List.of(fromO, fromNull, fromAD));
}
