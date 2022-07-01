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

import static com.hedera.mirror.web3.evm.utils.AdressUtils.asEvmAddress;
import static com.hedera.mirror.web3.evm.utils.AdressUtils.realmFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.AdressUtils.shardFromEvmAddress;
import static com.hedera.services.transaction.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.services.transaction.store.contracts.EntityAccess;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.services.transaction.store.contracts.WorldStateAccount;
import com.hedera.services.transaction.store.contracts.WorldStateTokenAccount;

@Data
public class SimulatedUpdater implements HederaWorldUpdater {

    private ContractRepository contractRepository;
    private AliasesResolver aliasesResolver;
    private SimulatedEntityAccess entityAccess;
    private CodeCache codeCache;

    private int numAllocatedIds = 0;
    private long sbhRefund = 0L;
    private long newContractNumId;

    private final List<Address> provisionalContractCreations = new LinkedList<>();
    protected final Set<Address> deletedAccounts = new HashSet<>();
    protected final Map<Address, UpdateTrackingLedgerAccount<Account>> updatedAccounts = new HashMap<>();

    //FUTURE WORK finish implementation when we introduce StackedUpdaters
    public SimulatedUpdater updater() {
        return new SimulatedUpdater();
    }

    @Override
    public EvmAccount createAccount(final Address addressOrAlias, final long nonce, final Wei balance) {
        final var address = aliasesResolver.resolveForEvm(addressOrAlias);
        final var newMutable = new UpdateTrackingLedgerAccount<>(address);
        newMutable.setNonce(nonce);
        newMutable.setBalance(balance);

        return new WrappedEvmAccount(track(newMutable));
    }

    @Override
    public EvmAccount getAccount(Address address) {
        final var extantMutable = updatedAccounts.get(address);
        if (extantMutable != null) {
            return new WrappedEvmAccount(extantMutable);
        } else if (deletedAccounts.contains(address)) {
            return null;
        } else {
            final var origin = get(address);
            if (origin == null) {
                return null;
            }
            final var newMutable = new UpdateTrackingLedgerAccount<>(origin);
            return new WrappedEvmAccount(track(newMutable));
        }
    }

    @Override
    public void deleteAccount(Address addressOrAlias) {
        final var address = aliasesResolver.resolveForEvm(addressOrAlias);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
        return new ArrayList<>(getUpdatedAccounts().values());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
        return new ArrayList<>(getDeletedAccounts());
    }

    @Override
    public void revert() {
        getDeletedAccounts().clear();
        getUpdatedAccounts().clear();

        while (numAllocatedIds != 0) {
            numAllocatedIds--;
            newContractNumId--;
        }
        sbhRefund = 0L;
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
        if (address == null) {
            return null;
        }
        if (entityAccess.isTokenAccount(address)) {
            return new WorldStateTokenAccount(address);
        }
        if (!isGettable(address)) {
            return null;
        }
        final long balance = entityAccess.getBalance(address);
        return new WorldStateAccount(address, Wei.of(balance), codeCache, entityAccess);
    }

    @Override
    public Address newContractAddress(Address sponsor) {
        numAllocatedIds++;
        newContractNumId = contractRepository.findLatestNum().get() + 1;

        final var sponsorBytes = sponsor.toArrayUnsafe();
        final var newContractEvmBytes = asEvmAddress(shardFromEvmAddress(sponsorBytes), realmFromEvmAddress(sponsorBytes), newContractNumId);
        return Address.wrap(Bytes.wrap(newContractEvmBytes));
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
        numAllocatedIds += n;
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

    protected UpdateTrackingLedgerAccount<Account> track(final UpdateTrackingLedgerAccount<Account> account) {
        final var address = account.getAddress();
        updatedAccounts.put(address, account);
        deletedAccounts.remove(address);
        return account;
    }

    private boolean isGettable(final Address address) {
        return entityAccess.isExtant(address) && !entityAccess.isDeleted(address) && !entityAccess.isDetached(address);
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
