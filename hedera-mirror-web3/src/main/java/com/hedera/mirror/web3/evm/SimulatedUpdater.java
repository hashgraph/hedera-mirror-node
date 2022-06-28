package com.hedera.mirror.web3.evm;

import static com.hedera.mirror.web3.evm.utils.AddressUtils.accountIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.AddressUtils.asContract;
import static com.hedera.services.transaction.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.services.transaction.store.contracts.EntityAccess;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;

public class SimulatedUpdater implements HederaWorldUpdater {

    @Autowired
    private AliasesResolver aliasesResolver;

    @Autowired
    private SimulatedEntityAccess entityAccess;

    private final List<ContractID> provisionalContractCreations = new LinkedList<>();
    private long sbhRefund = 0L;

    protected Set<Address> deletedAccounts = new HashSet<>();
    protected Map<Address, UpdateTrackingLedgerAccount<Account>> updatedAccounts = new HashMap<>();

    public SimulatedUpdater updater() {
        return new SimulatedUpdater();
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
    public void deleteAccount(Address addressOrAlias) {
        final var address = aliasesResolver.resolveForEvm(addressOrAlias);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
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
        commitSizeLimitedStorageTo(entityAccess);

        final var deletedAddresses = getDeletedAccountAddresses();
        deletedAddresses.forEach(address -> {
            final var accountId = accountIdFromEvmAddress(address);
            ensureExistence(accountId, entityAccess, provisionalContractCreations);
        });
        for (final var updatedAccount : updatedAccounts.values()) {
            if (updatedAccount.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE) {
                continue;
            }
            final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
            ensureExistence(accountId, entityAccess, provisionalContractCreations);
            if (updatedAccount.codeWasUpdated()) {
                entityAccess.storeCode(accountId, updatedAccount.getCode());
            }
        }
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        return Optional.empty();
    }

    @Override
    public Account get(Address address) {
        return null;
    }

    @Override
    public Address newContractAddress(Address sponsor) {
        return null;
    }

    /**
     * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is refunded for the first
     * allocation of new contract storage in order to prevent double charging the client.
     *
     * @return the amount of Gas to refund;
     */
    @Override
    public long getSbhRefund() {
        return sbhRefund;
    }

    @Override
    public void addSbhRefund(long refund) {
        sbhRefund = sbhRefund + refund;
    }

    @Override
    public void countIdsAllocatedByStacked(int n) {

    }

    private void commitSizeLimitedStorageTo(final EntityAccess entityAccess) {
        for (final var updatedAccount : updatedAccounts.values()) {
            final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
            // Note that we don't have the equivalent of an account-scoped storage trie, so we can't
            // do anything in particular when updated.getStorageWasCleared() is true. (We will address
            // this in our global state expiration implementation.)
            final var kvUpdates = updatedAccount.getUpdatedStorage();
            if (!kvUpdates.isEmpty()) {
                kvUpdates.forEach((key, value) -> entityAccess.putStorage(accountId, key, value));
            }
        }
        entityAccess.flushStorage();
    }

    private void ensureExistence(
            final AccountID accountId,
            final EntityAccess entityAccess,
            final List<ContractID> provisionalContractCreations
    ) {
        if (!entityAccess.isExtant(accountId)) {
            provisionalContractCreations.add(asContract(accountId));
        }
    }
}
