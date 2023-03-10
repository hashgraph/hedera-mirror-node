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
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public abstract class CachingStateFrame<Address, Account, Token> {
    // ⮕ Nominations for a _better name_ than `StateFrame` are open ...

    @NonNull
    protected final Optional<CachingStateFrame<Address, Account, Token>> parentFrame;

    @NonNull
    protected final UpdatableReferenceCache<Address, Account> accountCache;

    @NonNull
    protected final UpdatableReferenceCache<Address, Token> tokenCache;

    protected CachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address, Account, Token>> parentFrame,
            @NonNull final Class<Account> klassAccount,
            @NonNull final Class<Token> klassToken) {
        Objects.requireNonNull(parentFrame, "parentFrame");
        this.parentFrame = parentFrame;

        accountCache = new UpdatableReferenceCache<>(klassAccount);
        tokenCache = new UpdatableReferenceCache<>(klassToken);
    }

    @NonNull
    public abstract Optional<Account> getAccount(@NonNull final Address address);

    public abstract void setAccount(@NonNull final Address address, @NonNull final Account account);

    public abstract void deleteAccount(@NonNull final Address address);

    @NonNull
    public abstract Optional<Token> getToken(@NonNull final Address address);

    public abstract void setToken(@NonNull final Address address, @NonNull final Token token);

    public abstract void deleteToken(@NonNull final Address address);

    public abstract void updatesFromChild(@NonNull final CachingStateFrame<Address, Account, Token> childFrame);

    public void commit() {
        parentFrame.ifPresent(frame -> frame.updatesFromChild(this));
    }

    public @NonNull Optional<CachingStateFrame<Address, Account, Token>> getParent() {
        return parentFrame.map(o -> o);
    }

    public int height() {
        return parentFrame.map(pf -> 1 + pf.height()).orElse(1);
    }

    public @NonNull Optional<CachingStateFrame<Address, Account, Token>> next() {
        return parentFrame;
    }

    @VisibleForTesting
    public Pair<UpdatableReferenceCache<Address, Account>, UpdatableReferenceCache<Address, Token>>
            getInternalCaches() {
        return Pair.of(accountCache, tokenCache);
    }
}
