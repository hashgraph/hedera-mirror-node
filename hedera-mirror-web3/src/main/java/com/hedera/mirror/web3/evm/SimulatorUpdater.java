package com.hedera.mirror.web3.evm;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.Collection;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class SimulatorUpdater implements WorldUpdater {

    public SimulatorUpdater updater() {
        return new SimulatorUpdater();
    }

    @Override
    public EvmAccount createAccount(Address address, long l, Wei wei) {
        return null;
    }

    @Override
    public EvmAccount getAccount(Address address) {
        return null;
    }

    @Override
    public void deleteAccount(Address address) {

    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
        return null;
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
        return null;
    }

    @Override
    public void revert() {

    }

    @Override
    public void commit() {

    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        return Optional.empty();
    }

    @Override
    public Account get(Address address) {
        return null;
    }

    /**
     * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is refunded for the first
     * allocation of new contract storage in order to prevent double charging the client.
     *
     * @return the amount of Gas to refund;
     */
    public long getSbhRefund() {
        return 0L;
    }
}
