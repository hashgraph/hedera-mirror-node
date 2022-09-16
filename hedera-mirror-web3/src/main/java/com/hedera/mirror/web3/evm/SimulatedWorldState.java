package com.hedera.mirror.web3.evm;

import static com.hedera.mirror.web3.evm.utils.AddressUtils.asEvmAddress;
import static com.hedera.mirror.web3.evm.utils.AddressUtils.realmFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.AddressUtils.shardFromEvmAddress;
import static com.hedera.services.transaction.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.transaction.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.transaction.store.contracts.EntityAccess;
import com.hedera.services.transaction.store.contracts.HederaMutableWorldState;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.services.transaction.store.contracts.WorldStateAccount;
import com.hedera.services.transaction.store.contracts.WorldStateTokenAccount;

@Named
@RequiredArgsConstructor
public class SimulatedWorldState implements HederaMutableWorldState {
    private final List<Address> provisionalContractCreations = new LinkedList<>();
    private final CodeCache codeCache;
    private final SimulatedAliasManager simulatedAliasManager;
    private final SimulatedEntityAccess entityAccess;
    private final EntityRepository entityRepository;
    private final ContractRepository contractRepository;
    private final NftRepository nftRepository;
    private final TokenRepository tokenRepository;

    @Override
    public List<Address> getCreatedContractIds() {
        final var copy = new ArrayList<>(provisionalContractCreations);
        provisionalContractCreations.clear();
        return copy;
    }

    @Override
    public Address newContractAddress(Address sponsor) {
        final var newContractNumId = contractRepository.findLatestNum().orElseThrow() + 1;

        final var sponsorBytes = sponsor.toArrayUnsafe();
        final var newContractEvmBytes =
                asEvmAddress(
                        shardFromEvmAddress(sponsorBytes),
                        realmFromEvmAddress(sponsorBytes),
                        newContractNumId);
        return Address.wrap(Bytes.wrap(newContractEvmBytes));
    }

    @Override
    public Updater updater() {
        return new Updater(this, simulatedAliasManager, entityAccess, entityRepository, nftRepository, tokenRepository);
    }

    @Override
    public Hash rootHash() {
        return Hash.EMPTY;
    }

    @Override
    public Hash frontierRootHash() {
        return rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Account get(final @Nullable Address address) {
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
        final var accountNonce =
                entityRepository.findAccountNonceByAddress(address.toArray());
        final long nonce = accountNonce.isEmpty() ? 0L : accountNonce.get();

        return new WorldStateAccount(address, Wei.of(balance), nonce, codeCache, entityAccess);
    }

    private boolean isGettable(final Address address) {
        return entityAccess.isExtant(address)
                && !entityAccess.isDeleted(address)
                && !entityAccess.isDetached(address);
    }

    public static class Updater extends AbstractLedgerWorldUpdater<HederaMutableWorldState, Account>
            implements HederaWorldUpdater {
        private int numAllocatedIds = 0;
        private long sbhRefund = 0L;

        private final SimulatedAliasManager simulatedAliasManager;
        private final SimulatedEntityAccess simulatedEntityAccess;
        private final EntityRepository entityRepository;
        private final NftRepository nftRepository;
        private final TokenRepository tokenRepository;

        protected Updater(
                final SimulatedWorldState world, final SimulatedAliasManager simulatedAliasManager,
                final SimulatedEntityAccess simulatedEntityAccess, final EntityRepository entityRepository, final NftRepository nftRepository,
                final TokenRepository tokenRepository) {
            super(
                    world,
                    simulatedAliasManager,
                    simulatedEntityAccess,
                    entityRepository,
                    nftRepository,
                    tokenRepository);
            this.simulatedAliasManager = simulatedAliasManager;
            this.simulatedEntityAccess = simulatedEntityAccess;
            this.entityRepository = entityRepository;
            this.nftRepository = nftRepository;
            this.tokenRepository = tokenRepository;
        }

        @Override
        protected Account getForMutation(final Address address) {
            final SimulatedWorldState wrapped = (SimulatedWorldState) wrappedWorldView();
            return wrapped.get(address);
        }

        @Override
        public Address newContractAddress(final Address sponsor) {
            numAllocatedIds++;
            return wrappedWorldView().newContractAddress(sponsor);
        }

        @Override
        public long getSbhRefund() {
            return sbhRefund;
        }

        @Override
        public void addSbhRefund(long refund) {
            sbhRefund = sbhRefund + refund;
        }

        @Override
        public void revert() {
            super.revert();
            numAllocatedIds = 0;
            sbhRefund = 0L;
        }

        @Override
        public void countIdsAllocatedByStacked(final int n) {
            numAllocatedIds += n;
        }

        @Override
        public void commit() {
            final SimulatedWorldState wrapped = (SimulatedWorldState) wrappedWorldView();
            final var entityAccess = wrapped.entityAccess;
            final var updatedAccounts = getUpdatedAccounts();

            trackNewlyCreatedAccounts(
                    entityAccess,
                    wrapped.provisionalContractCreations,
                    getDeletedAccountAddresses(),
                    updatedAccounts);

            commitSizeLimitedStorageTo(entityAccess, updatedAccounts);
        }

        private void trackNewlyCreatedAccounts(
                final EntityAccess entityAccess,
                final List<Address> provisionalCreations,
                final Collection<Address> deletedAddresses,
                final Collection<UpdateTrackingLedgerAccount<Account>> updatedAccounts) {
            deletedAddresses.forEach(
                    address -> {
                        trackIfNewlyCreated(address, entityAccess, provisionalCreations);
                    });
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE) {
                    continue;
                }
                trackIfNewlyCreated(updatedAccount.getAddress(), entityAccess, provisionalCreations);
            }
        }

        private void trackIfNewlyCreated(
                final Address accountAddress,
                final EntityAccess entityAccess,
                final List<Address> provisionalContractCreations) {
            if (!entityAccess.isExtant(accountAddress)) {
                provisionalContractCreations.add(accountAddress);
            }
        }

        private void commitSizeLimitedStorageTo(
                final EntityAccess entityAccess,
                final Collection<UpdateTrackingLedgerAccount<Account>> updatedAccounts) {
            entityAccess.flushStorage();
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.codeWasUpdated()) {
                    entityAccess.storeCode(updatedAccount.getAddress(), updatedAccount.getCode());
                }
            }
        }

        @Override
        public WorldUpdater updater() {
            return new SimulatedStackedWorldStateUpdater(this, wrappedWorldView(), simulatedAliasManager,
                    simulatedEntityAccess, entityRepository, nftRepository, tokenRepository);
        }
    }
}

