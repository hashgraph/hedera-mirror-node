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
import java.util.EmptyStackException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings(
        "java:S1192") // "define a constant instead of duplicating this literal" - worse readability if applied to small
// literals
public class StackedStateFrames<K> {

    @NonNull
    protected CachingStateFrame<K> stackBase; // fixed "base" of stack: a R/O cache on top of the DB

    protected CachingStateFrame<K> stack; // current top of stack (which is all linked together)

    @NonNull
    protected final List<GroundTruthAccessor<K, ?>> accessors;

    @SuppressWarnings("rawtypes")
    @NonNull
    protected final Class[] valueClasses;

    public StackedStateFrames(@NonNull final List<GroundTruthAccessor<K, ?>> accessors) {
        this.accessors = accessors;
        this.valueClasses = accessors.stream()
                .map(GroundTruthAccessor::getVClass)
                .distinct()
                .toArray(Class[]::new);

        if (valueClasses.length != accessors.size())
            throw new IllegalArgumentException("accessors must be for distinct types");
        if (1
                != accessors.stream()
                        .map(GroundTruthAccessor::getKClass)
                        .map(Class::getTypeName)
                        .distinct()
                        .count()) throw new IllegalArgumentException("key types for all accessors must be the same");

        // TODO: probably takes the database connection thing/abstraction as a parameter and saves it away
        final var database = new DatabaseBackedStateFrame<K>(accessors, valueClasses);
        stack = stackBase = new ROCachingStateFrame<>(Optional.of(database), valueClasses);
        // Initial state is just the R/O cache on top of the database.  You really need to do a
        // `push()` before you can expect to write anything to this state
    }

    public int height() {
        return stack.height() - stackBase.height();
    }

    public int cachedFramesDepth() {
        return stack.height();
    }

    @NonNull
    public CachingStateFrame<K> top() {
        return stack;
    }

    @NonNull
    public CachingStateFrame<K> push() {
        stack = new RWCachingStateFrame<>(Optional.of(stack), valueClasses);
        return stack;
    }

    public void pop() {
        if (stack == stackBase) throw new EmptyStackException();
        stack = stack.getUpstream().orElseThrow(EmptyStackException::new);
    }

    public void resetToBase() {
        stack = stackBase;
    }

    @NonNull
    public Class<?>[] getValueClasses() {
        return valueClasses;
    }

    @NonNull
    public CachingStateFrame<K> push(@NonNull final CachingStateFrame<K> frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.getUpstream() != Optional.ofNullable(stack))
            throw new IllegalArgumentException("frame argument must have current TOS as its upstream");
        stack = frame;
        return stack;
    }

    @VisibleForTesting
    @NonNull
    public CachingStateFrame<K> replaceEntireStack(@NonNull final CachingStateFrame<K> frame) {
        Objects.requireNonNull(frame, "frame");
        stack = stackBase = frame;
        return stack;
    }
}
