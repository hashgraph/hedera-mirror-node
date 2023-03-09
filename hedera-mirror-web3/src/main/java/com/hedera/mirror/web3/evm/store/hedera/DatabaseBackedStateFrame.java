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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;

public class DatabaseBackedStateFrame extends CachingStateFrame {

    public DatabaseBackedStateFrame(/*some kind of database accessor is passed in here*/ ) {
        super(Optional.empty());
        // TODO: get ready to access the database to find accounts
    }

    @NonNull
    @Override
    public Optional<Account> getAccount(@NonNull final Address address) {
        Objects.requireNonNull(address, "address");
        // TODO: This is where we go to the database and get the proper account
        return Optional.empty();
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
    public void updatesFromChild(@NonNull final CachingStateFrame childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a database-backed StateFrame (oddly enough)");
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException("cannot commit a database-backed StateFrame");
    }
}
