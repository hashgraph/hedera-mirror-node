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

public class RWCachingStateFrame<Address, Account, Token> extends CachingStateFrame<Address, Account, Token> {

    public RWCachingStateFrame(
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

        accountCache.update(address, account);
    }

    @Override
    public void deleteAccount(@NonNull final Address address) {
        Objects.requireNonNull(address);
        accountCache.delete(address);
    }

    @Override
    public @NonNull Optional<Token> getToken(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        final var token = tokenCache.get(address);
        return switch (token.state()) {
            case NOT_YET_FETCHED -> parentFrame.flatMap(parent -> {
                final var upstreamToken = parent.getToken(address);
                tokenCache.fill(address, upstreamToken.orElse(null));
                return upstreamToken;
            });
            case PRESENT, UPDATED -> Optional.of(token.value());
            case MISSING, DELETED -> Optional.empty();
        };
    }

    @Override
    public void setToken(@NonNull final Address address, @NonNull final Token token) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(token, "token");
        tokenCache.update(address, token);
    }

    @Override
    public void deleteToken(@NonNull final Address address) {
        Objects.requireNonNull(address);
        tokenCache.delete(address);
    }

    @Override
    public void updatesFromChild(@NonNull final CachingStateFrame<Address, Account, Token> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        accountCache.coalesceFrom(childFrame.accountCache);
        tokenCache.coalesceFrom(childFrame.tokenCache);
    }
}
