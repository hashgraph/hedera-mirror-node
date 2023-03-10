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
import org.apache.commons.lang3.tuple.Pair;

public class StackedStateFrames<Address, Account, Token> {

    @NonNull
    final CachingStateFrame<Address, Account, Token> stackBase; // fixed "base" of stack: a R/O cache on top of the DB

    CachingStateFrame<Address, Account, Token> stack = null; // current top of stack (which is all linked together)

    @NonNull
    final Class<Account> klassAccount;

    @NonNull
    final Class<Token> klassToken;

    public StackedStateFrames(
            @NonNull final Pair<Accessor<Address, Account>, Accessor<Address, Token>> accessors,
            @NonNull final Class<Account> klassAccount,
            @NonNull final Class<Token> klassToken) {
        this.klassAccount = klassAccount;
        this.klassToken = klassToken;

        // TODO: probably takes the database connection thing/abstraction as a parameter and saves it away
        final var database = new DatabaseBackedStateFrame<Address, Account, Token>(accessors, klassAccount, klassToken);
        stack = stackBase = new ROCachingStateFrame<>(Optional.of(database), klassAccount, klassToken);
        // Initial state is just the R/O cache on top of the database.  You really need to do a
        // `push()` before you can expect to write anything to this state
    }

    public int height() {
        return stack.height() - stackBase.height();
    }

    public int cachedFramesDepth() {
        return stack.height();
    }

    public @NonNull CachingStateFrame<Address, Account, Token> top() {
        return stack;
    }

    public @NonNull CachingStateFrame<Address, Account, Token> push() {
        stack = new RWCachingStateFrame<>(Optional.of(stack), klassAccount, klassToken);
        return stack;
    }

    public void pop() {
        if (stack == stackBase) throw new EmptyStackException();
        stack = stack.getParent().orElseThrow(EmptyStackException::new);
    }
}
