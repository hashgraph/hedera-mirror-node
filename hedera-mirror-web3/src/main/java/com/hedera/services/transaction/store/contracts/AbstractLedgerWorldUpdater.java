package com.hedera.services.transaction.store.contracts;

import static com.hedera.services.transaction.models.Account.ECDSA_KEY_ALIAS_PREFIX;
import static com.hedera.services.transaction.models.Account.ECDSA_SECP256K1_ALIAS_SIZE;
import static com.hedera.services.transaction.models.Account.EVM_ADDRESS_SIZE;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.evm.SimulatedEntityAccess;
import com.hedera.mirror.web3.evm.SimulatedWorldState;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.transaction.ethereum.EthTxSigs;

/**
 * Provides implementation help for both "base" and "stacked" {@link WorldUpdater}s.
 *
 * <p>The key internal invariant of the class is that it makes consistent use of its (1) {@code
 * deletedAccounts} set and {@code updatedAccounts} map; and (2) the {@code accounts} tracking
 * ledger from its {@code trackingLedgers}.
 *
 * <p>Without running an HTS precompile, the "internal" information flow is one-way, from (1) to
 * (2). There are three cases:
 *
 * <ol>
 *   <li>When an address is added to the {@code deletedAccounts}, it is also marked deleted in the
 *       {@code accounts} ledger.
 *   <li>When an address is added to the {@code updatedAccounts} map via {@link
 *       AbstractLedgerWorldUpdater#createAccount(Address, long, Wei)}, it is also spawned in the
 *       {@code accounts} ledger.
 *   <li>When {@link UpdateTrackingLedgerAccount#setBalance(Wei)} is called on a (mutable) tracking
 *       account, the same balance change is made in the {@code accounts} ledger.
 * </ol>
 *
 * When an HTS precompile is run, the commit to the {@code acccounts} ledger is then intercepted so
 * that balance changes are reflected in the {@code updatedAccounts} map.
 *
 * <p>Concrete subclasses must then manage the "external" information flow from these data
 * structures to their wrapped {@link WorldView} in a {@link HederaWorldUpdater#commit()}
 * implementation. This will certainly involve calling { WorldLedgers#commit()}, and then
 * merging the {@code deletedAccounts} and {@code updatedAccounts} with the parent {@link
 * org.hyperledger.besu.evm.worldstate.WorldState} in some way.
 *
 * @param <A> the most specialized account type to be updated
 * @param <W> the most specialized world updater to be used
 */
public abstract class AbstractLedgerWorldUpdater<W extends WorldView, A extends Account>
        implements WorldUpdater {
    private final W world;
    private final SimulatedAliasManager simulatedAliasManager;
    private final SimulatedEntityAccess simulatedEntityAccess;
    private final EntityRepository entityRepository;

    protected Set<Address> deletedAccounts = new HashSet<>();
    protected Map<Address, UpdateTrackingLedgerAccount<A>> updatedAccounts = new HashMap<>();

    protected AbstractLedgerWorldUpdater(final W world, final SimulatedAliasManager simulatedAliasManager,
            final SimulatedEntityAccess simulatedEntityAccess,
            final EntityRepository entityRepository) {
        this.world = world;
        this.simulatedAliasManager = simulatedAliasManager;
        this.simulatedEntityAccess = simulatedEntityAccess;
        this.entityRepository = entityRepository;
    }

    /**
     * Given an address, returns an account that can be mutated <b>with the assurance</b> that these
     * mutations will be tracked in the change-set represented by this {@link WorldUpdater}; and
     * either committed or reverted atomically with all other mutations in the change-set.
     *
     * @param address the address of interest
     * @return a tracked mutable account for the given address
     */
    protected abstract A getForMutation(Address address);

    @Override
    public EvmAccount createAccount(
            final Address addressOrAlias, final long nonce, final Wei balance) {
        final var address = simulatedAliasManager.resolveForEvm(addressOrAlias);

        final var newMutable = new UpdateTrackingLedgerAccount<A>(address);
        newMutable.setNonce(nonce);
        newMutable.setBalance(balance);

        return new WrappedEvmAccount(track(newMutable));
    }

    @Override
    public Account get(final Address addressOrAlias) {
        if (!addressOrAlias.equals(canonicalAddress(addressOrAlias))) {
            return null;
        }

        final var address = simulatedAliasManager.resolveForEvm(addressOrAlias);

        final var extantMutable = this.updatedAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        } else {
            if (this.deletedAccounts.contains(address)) {
                return null;
            }
            if (this.world.getClass() == SimulatedWorldState.class) {
                return this.world.get(address);
            }
            return this.world.get(addressOrAlias);
        }
    }

    private Address canonicalAddress(final Address addressOrAlias) {
        return getAddressOrAlias(addressOrAlias);
    }

    private Address getAddressOrAlias(final Address address) {
        final var account = entityRepository.findAccountByAddress(address.toArray()).orElse(null);
        ByteString alias = ByteString.EMPTY;
        if(account==null) {
            return address;
        }

        if(account.getAlias()!=null) {
            alias = ByteString.copyFrom(account.getAlias());
        }

        if (!alias.isEmpty()) {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE
                    && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                byte[] value = EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                if (value != null) {
                    return Address.wrap(Bytes.wrap(value));
                }
            }
        }
        return address;
    }

    @Override
    public EvmAccount getAccount(final Address address) {
        final var extantMutable = updatedAccounts.get(address);
        if (extantMutable != null) {
            return new WrappedEvmAccount(extantMutable);
        } else if (deletedAccounts.contains(address)) {
            return null;
        } else {
            final var origin = getForMutation(address);
            if (origin == null) {
                return null;
            }
            final var newMutable =
                    new UpdateTrackingLedgerAccount<>(origin);
            return new WrappedEvmAccount(track(newMutable));
        }
    }

    @Override
    public void deleteAccount(final Address addressOrAlias) {
        final var address = simulatedAliasManager.resolveForEvm(addressOrAlias);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        if (world instanceof WorldUpdater updater) {
            return Optional.of(updater);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
        return new ArrayList<>(getUpdatedAccounts());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
        return new ArrayList<>(deletedAccounts);
    }

    @Override
    public void revert() {
        getDeletedAccounts().clear();
        getUpdatedAccounts().clear();
    }

    protected UpdateTrackingLedgerAccount<A> track(final UpdateTrackingLedgerAccount<A> account) {
        final var address = account.getAddress();
        updatedAccounts.put(address, account);
        deletedAccounts.remove(address);
        return account;
    }

    protected W wrappedWorldView() {
        return world;
    }

    protected Collection<Address> getDeletedAccounts() {
        return deletedAccounts;
    }

    protected Collection<UpdateTrackingLedgerAccount<A>> getUpdatedAccounts() {
        return updatedAccounts.values();
    }
}

