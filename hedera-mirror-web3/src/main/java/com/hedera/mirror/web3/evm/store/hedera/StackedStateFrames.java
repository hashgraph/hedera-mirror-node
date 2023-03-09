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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EmptyStackException;
import java.util.Optional;

public class StackedStateFrames {

    final CachingStateFrame stackBase;
    CachingStateFrame stack = null;

    public StackedStateFrames() {
        // TODO: probably takes the database connection thing/abstraction as a parameter and saves it away
        final var database = new DatabaseBackedStateFrame(/*some kind of database accessor goes here*/ );
        final var roCache = new ROCachingStateFrame(Optional.of(database));
        stack = stackBase = roCache;
        // Initial state is just the R/O cache on top of the database.  You really need to do a
        // `push()` before you can expect to write anything to this state
    }

    public @NonNull StateFrame top() {
        return stack;
    }

    public @NonNull StateFrame push() {
        stack = new RWCachingStateFrame(Optional.of(stack));
        return stack;
    }

    public void pop() {
        if (stack == stackBase) throw new EmptyStackException();
        stack = (CachingStateFrame) (stack.getParent().get());
        // TODO: Two things need some reworking here.  First, the interface `StateFrame` and its relationship to the
        // abstract class `CachingStateFrame`.  Second, why isn't the parent of the right type here?
        // (Maybe don't need `StateFrame`?)
    }
}
