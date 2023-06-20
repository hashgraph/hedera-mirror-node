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

package com.hedera.mirror.web3.evm.store.contract;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.Collection;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public abstract class AbstractLedgerWorldUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerEvmWorldUpdater<W, A> {

    private final Store store;

    protected AbstractLedgerWorldUpdater(W world, AccountAccessor accountAccessor, Store store) {
        super(world, accountAccessor);
        this.store = store;
    }

    protected AbstractLedgerWorldUpdater(
            W world,
            AccountAccessor accountAccessor,
            TokenAccessor tokenAccessor,
            HederaEvmEntityAccess hederaEvmEntityAccess,
            Store store) {
        super(world, accountAccessor, tokenAccessor, hederaEvmEntityAccess);
        this.store = store;
    }

    @Override
    public void deleteAccount(Address address) {
        store.deleteAccount(address);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
    }

    protected Collection<Address> getDeletedAccounts() {
        return deletedAccounts;
    }
}
