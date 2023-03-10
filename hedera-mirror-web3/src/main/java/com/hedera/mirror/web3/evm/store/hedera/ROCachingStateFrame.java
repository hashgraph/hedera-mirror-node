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
import java.util.Objects;
import java.util.Optional;

public class ROCachingStateFrame<Address, Account, Token> extends CachingStateFrame<Address, Account, Token> {

    public ROCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address, Account, Token>> parentFrame,
            @NonNull final Class<Account> klassAccount,
            @NonNull final Class<Token> klassToken) {
        super(parentFrame, klassAccount, klassToken);
    }

    @Override
    public @NonNull Optional<Account> getAccount(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        final var account = accountCache.get(address);
        return switch (account.state()) {
            case NOT_YET_FETCHED -> parentFrame.flatMap(parent -> {
                final var upstreamAccount = parent.getAccount(address);
                accountCache.fill(address, upstreamAccount.orElse(null));
                return upstreamAccount;
            });
            case PRESENT, UPDATED -> Optional.of(account.value());
            case MISSING, DELETED -> Optional.empty();
        };
    }

    @Override
    public void setAccount(@NonNull final Address address, @NonNull final Account account) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(account, "account");
        throw new UnsupportedOperationException("cannot write to a R/O cache");
    }

    @Override
    public void deleteAccount(@NonNull final Address address) {
        Objects.requireNonNull(address);
        throw new UnsupportedOperationException("cannot delete account from a R/O cache");
    }

    @Override
    public @NonNull Optional<Token> getToken(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        final var token = tokenCache.get(address);
        return switch (token.state()) {
            case NOT_YET_FETCHED -> {
                if (parentFrame.isEmpty()) yield Optional.empty();
                final var upstreamToken = parentFrame.get().getToken(address);
                tokenCache.fill(address, upstreamToken.orElse(null));
                yield upstreamToken;
            }
            case PRESENT, UPDATED -> Optional.of(token.value());
            case MISSING, DELETED -> Optional.empty();
        };
    }

    @Override
    public void setToken(@NonNull final Address address, @NonNull final Token token) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(token, "token");
        throw new UnsupportedOperationException("cannot write to a R/O cache");
    }

    @Override
    public void deleteToken(@NonNull final Address address) {
        Objects.requireNonNull(address);
        throw new UnsupportedOperationException("cannot delete token from a R/O cache");
    }

    @Override
    public void updatesFromChild(@NonNull final CachingStateFrame<Address, Account, Token> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a R/O cache");
    }
}
