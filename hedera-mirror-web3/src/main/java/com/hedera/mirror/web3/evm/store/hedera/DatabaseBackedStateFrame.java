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
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;

@SuppressWarnings("java:S1192") // "string literals should not be duplicated"
public class DatabaseBackedStateFrame<Address, Account, Token> extends CachingStateFrame<Address, Account, Token> {

    final Accessor<Address, Account> accountAccessor;
    final Accessor<Address, Token> tokenAccessor;

    public DatabaseBackedStateFrame(
            @NonNull final Pair<Accessor<Address, Account>, Accessor<Address, Token>> databaseAccessor,
            @NonNull final Class<Account> klassAccount,
            @NonNull final Class<Token> klassToken) {
        super(Optional.empty(), klassAccount, klassToken);
        accountAccessor = databaseAccessor.getLeft();
        tokenAccessor = databaseAccessor.getRight();
    }

    @NonNull
    @Override
    public Optional<Account> getAccount(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        return accountAccessor.get(address);
    }

    @Override
    public void setAccount(@NonNull final Address address, @NonNull final Account account) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(account, "account");
        throw new UnsupportedOperationException("cannot add/update an account in a database-backed StateFrame");
    }

    @Override
    public void deleteAccount(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        throw new UnsupportedOperationException("cannot delete account in a database-backed StateFrame");
    }

    @Override
    public @NonNull Optional<Token> getToken(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        return tokenAccessor.get(address);
    }

    @Override
    public void setToken(@NonNull final Address address, @NonNull final Token token) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(token, "token");
        throw new UnsupportedOperationException("cannot add/update a token in a database-backed StateFrame");
    }

    @Override
    public void deleteToken(@NonNull final Address address) {
        Objects.requireNonNull(address);
        throw new UnsupportedOperationException("cannot delete token in a database-backed StateFrame");
    }

    @Override
    public void updatesFromChild(@NonNull final CachingStateFrame<Address, Account, Token> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a database-backed StateFrame (oddly enough)");
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException("cannot commit to a database-backed StateFrame (oddly enough)");
    }
}
