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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;

public abstract class CachingStateFrame implements StateFrame {

    protected final @NonNull Optional<CachingStateFrame> parentFrame;
    protected final @NonNull Map<Address, Account> accounts = new HashMap<>();
    protected final @NonNull Set<Address> deletedAccounts = new HashSet<>();

    protected CachingStateFrame(final @NonNull Optional<CachingStateFrame> parentFrame) {
        Objects.requireNonNull(parentFrame, "parentFrame");
        this.parentFrame = parentFrame;
    }

    public abstract void updatesFromChild(final @NonNull CachingStateFrame childFrame);

    @Override
    public void commit() {
        parentFrame.ifPresent(frame -> frame.updatesFromChild(this));
    }

    @Override
    public @NonNull Optional<StateFrame> getParent() {
        return parentFrame.map(o -> o);
    }

    protected @NonNull Map<Address, Account> getAccounts() {
        return accounts;
    }
}
