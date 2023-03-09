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
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account; // n.b.: This is _not_ the right Account type
// .                                                but we don't have the right Account type yet

public interface StateFrame {

    @NonNull
    Optional<Account> getAccount(final @NonNull Address address);

    void setAccount(final @NonNull Address address, final @NonNull Account account);

    void deleteAccount(final @NonNull Address address);

    // TODO: and the same for tokens ...

    void commit();

    @NonNull
    Optional<StateFrame> getParent();
}

// ⮕ Nominations for a _better name_ than `StateFrame` are open ...
