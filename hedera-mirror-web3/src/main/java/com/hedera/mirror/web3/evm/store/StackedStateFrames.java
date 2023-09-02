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

import static com.hedera.mirror.web3.common.ThreadLocalHolder.stack;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import jakarta.inject.Named;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;

@Named
public class StackedStateFrames<K> {

    /** All the database accessors for the value types this stacked cache can hold */
    @NonNull
    protected final List<DatabaseAccessor<Object, ?>> accessors;
    /** All the `Class`es for the value types this stacked cache can hold */
    @NonNull
    protected final Class<?>[] valueClasses;
    /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
    @Getter
    @NonNull
    protected CachingStateFrame<Object> stackBase;

    /** Create a `StackedStackFrames` stacked cache at base level given the database accessors for all the value
     * types this cache will hold.
     */
    public StackedStateFrames(@NonNull final List<DatabaseAccessor<Object, ?>> accessors) {
        this.accessors = accessors;
        this.valueClasses = accessors.stream()
                .map(DatabaseAccessor::getValueClass)
                .distinct()
                .toArray(Class[]::new);

        if (valueClasses.length != accessors.size()) {
            throw new IllegalArgumentException("Accessors must be for distinct types");
        }
        final var nUniqueAccessors = accessors.stream()
                .map(DatabaseAccessor::getKeyClass)
                .map(Class::getTypeName)
                .distinct()
                .count();
        if (nUniqueAccessors != 1) {
            throw new IllegalArgumentException("Key types for all accessors must be the same");
        }

        final var database = new DatabaseBackedStateFrame<Object>(accessors, valueClasses);
        stackBase = new ROCachingStateFrame<Object>(Optional.of(database), valueClasses);

        // Initial state is a R/O cache on top of the database. You should use push() to create a new R/W cache layer on
        // top of the stack,
        // so that you can commit specific changes from a nested transaction
    }

    public CachingStateFrame<Object> getEmptyStackBase() {
        final var database = new DatabaseBackedStateFrame<Object>(accessors, valueClasses);
        return new ROCachingStateFrame<Object>(Optional.of(database), valueClasses);
    }

    /** Return the "visible"/"effective" height of the stacked cache _only including_ those frames you've pushed on top
     * of it (after initial construction).
     */
    public int height() {
        return stack.get().height() - stackBase.height();
    }

    /** Return the _total_ height (aka depth) of the stacked cache _including_ the always-present stack base of a
     * RO-cache frame on top of the DB-based frame.
     */
    public int cachedFramesDepth() {
        return stack.get().height();
    }

    /** Get the top of the stacked cache.  (Make your queries of the cache here, at the top: they'll be propagated
     * upstream as needed.)
     */
    @NonNull
    public CachingStateFrame<Object> top() {
        return stack.get();
    }

    /** Push a new RW-frame cache on top of the stacked cache. */
    @NonNull
    public CachingStateFrame<Object> push() {
        stack.set(new RWCachingStateFrame<Object>(Optional.of(stack.get()), valueClasses));
        return stack.get();
    }

    /** Pop a frame's cache from the top of the stacked cache. */
    public void pop() {
        if (stack.get() == stackBase) {
            throw new EmptyStackException();
        }
        stack.set(stack.get().getUpstream().orElseThrow(EmptyStackException::new));
    }

    /** Chop the stack back to its base. This keeps the most-upstream-layer which connects to the database, and the
     * `ROCachingStateFrame` on top of it.  Therefore, everything already read from the database is still present,
     * unchanged, in the stacked cache.  (Usage case is the multiple calls to `eth_estimateGas` in order to "binary
     * search" to the closest gas approximation for a given contract call: The _first_ call is the only one that actually
     * hits the database (via the database accessors), all subsequent executions will fetch the same values
     * (required!) from the RO-cache without touching the database again - if you cut back the stack between executions
     * using this method.)
     */
    public void resetToBase() {
        stack.set(stackBase);
    }

    /** Get the classes of all the value types this stacked cache can hold. */
    @NonNull
    public Class<?>[] getValueClasses() {
        return valueClasses;
    }

    /** It may happen that you want to push some special frame type on the stack, not a standard `RWCachingStateFrame`.
     * For example, a spy.  You can do that with this method, but be sure your new TOS has the current TOS as its
     * upstream.
     */
    @VisibleForTesting
    @NonNull
    CachingStateFrame<Object> push(@NonNull final CachingStateFrame<Object> frame) {
        if (!frame.getUpstream().equals(Optional.of(stack.get()))) {
            throw new IllegalArgumentException("Frame argument must have current TOS as its upstream");
        }
        stack.set(frame);
        return stack.get();
    }

    /** For test purposes only you may want to step on the entire stack, including the stack base. (You might want to
     * replace the upstream-most `DatabaseStackedStateFrame` with some more agreeable thing, a mock, or just a
     * basic replacement.)
     */
    @VisibleForTesting
    @NonNull
    CachingStateFrame<Object> replaceEntireStack(@NonNull final CachingStateFrame<Object> frame) {
        stack.set(frame);
        stackBase = frame;
        return stack.get();
    }
}
