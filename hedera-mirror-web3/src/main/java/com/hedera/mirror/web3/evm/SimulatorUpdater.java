package com.hedera.mirror.web3.evm;

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
