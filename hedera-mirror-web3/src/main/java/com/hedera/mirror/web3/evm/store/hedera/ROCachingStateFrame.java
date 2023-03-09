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

public class ROCachingStateFrame extends CachingStateFrame {

    public ROCachingStateFrame(final @NonNull Optional<CachingStateFrame> parentFrame) {
        super(parentFrame);
    }

    @Override
    public @NonNull Optional<Account> getAccount(final @NonNull Address address) {
        Objects.requireNonNull(address, "address");
        if (accounts.containsKey(address)) return Optional.of(accounts.get(address));
        return parentFrame.flatMap(cachingStateFrame -> cachingStateFrame.getAccount(address));
    }

    @Override
    public void setAccount(final @NonNull Address address, final @NonNull Account account) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(account, "account");
        throw new UnsupportedOperationException("cannot write to a R/O cache");
    }

    @Override
    public void deleteAccount(final @NonNull Address address) {
        Objects.requireNonNull(address);
        throw new UnsupportedOperationException("cannot delete from a R/O cache");
    }

    @Override
    public void updatesFromChild(final @NonNull CachingStateFrame childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a R/O cache");
    }
}
