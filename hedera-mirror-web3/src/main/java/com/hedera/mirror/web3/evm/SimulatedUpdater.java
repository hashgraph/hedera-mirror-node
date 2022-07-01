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

import static com.hedera.services.transaction.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import com.hedera.services.transaction.store.contracts.EntityAccess;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;

@Data
public class SimulatedUpdater implements HederaWorldUpdater {

    private AliasesResolver aliasesResolver;
    private SimulatedEntityAccess entityAccess;

    private final List<Address> provisionalContractCreations = new LinkedList<>();
    private long sbhRefund = 0L;

    protected Set<Address> deletedAccounts = new HashSet<>();
    protected Map<Address, UpdateTrackingLedgerAccount<Account>> updatedAccounts = new HashMap<>();

    public SimulatedUpdater updater() {
        return new SimulatedUpdater();
    }

    //FUTURE WORK to be implemented
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
            ensureExistence(address, entityAccess, provisionalContractCreations);
        });
        for (final var updatedAccount : updatedAccounts.values()) {
            if (updatedAccount.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE) {
                continue;
            }
            final var accountAddress = updatedAccount.getAddress();
            ensureExistence(accountAddress, entityAccess, provisionalContractCreations);
            if (updatedAccount.codeWasUpdated()) {
                entityAccess.storeCode(accountAddress, updatedAccount.getCode());
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
            // Note that we don't have the equivalent of an account-scoped storage trie, so we can't
            // do anything in particular when updated.getStorageWasCleared() is true. (We will address
            // this in our global state expiration implementation.)
            final var kvUpdates = updatedAccount.getUpdatedStorage();
            if (!kvUpdates.isEmpty()) {
                kvUpdates.forEach((key, value) -> entityAccess.putStorage(updatedAccount.getAddress(), key, value));
            }
        }
        entityAccess.flushStorage();
    }

    private void ensureExistence(
            final Address accountAddress,
            final EntityAccess entityAccess,
            final List<Address> provisionalContractCreations
    ) {
        if (!entityAccess.isExtant(accountAddress)) {
            provisionalContractCreations.add(accountAddress);
        }
    }
}
