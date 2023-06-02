/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class MockLedgerWorldUpdater extends AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account> {

    public MockLedgerWorldUpdater(final HederaEvmWorldState world, final AccountAccessor accountAccessor) {
        super(world, accountAccessor);
    }

    @Override
    public Account getForMutation(Address address) {
        return null;
    }

    @Override
    public EvmAccount createAccount(Address address) {
        return super.createAccount(address);
    }

    @Override
    public EvmAccount getOrCreate(Address address) {
        return super.getOrCreate(address);
    }

    @Override
    public EvmAccount getOrCreateSenderAccount(Address address) {
        return super.getOrCreateSenderAccount(address);
    }

    @Override
    public EvmAccount getSenderAccount(MessageFrame frame) {
        return super.getSenderAccount(frame);
    }

    @Override
    public void commit() {}
}
