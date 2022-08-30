package com.hedera.mirror.web3.evm;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.services.transaction.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.transaction.store.contracts.AbstractStackedLedgerUpdater;
import com.hedera.services.transaction.store.contracts.HederaMutableWorldState;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.services.transaction.store.contracts.WorldStateTokenAccount;

public class SimulatedStackedWorldStateUpdater
        extends AbstractStackedLedgerUpdater<HederaMutableWorldState, Account>
        implements HederaWorldUpdater {

    private static final byte[] NON_CANONICAL_REFERENCE = new byte[20];
    private final HederaMutableWorldState worldState;
    private final SimulatedAliasManager simulatedAliasManager;
    private final SimulatedEntityAccess entityAccess;
    private final EntityRepository entityRepository;
    private final NftRepository nftRepository;
    private final TokenRepository tokenRepository;

    private long sbhRefund = 0L;
    private int numAllocatedIds = 0;

    public SimulatedStackedWorldStateUpdater(
            final AbstractLedgerWorldUpdater<HederaMutableWorldState, Account> updater,
            final HederaMutableWorldState worldState,
            final SimulatedAliasManager simulatedAliasManager,
            final SimulatedEntityAccess simulatedEntityAccess,
            final EntityRepository entityRepository,
            final NftRepository nftRepository,
            final TokenRepository tokenRepository) {
        super(updater, simulatedAliasManager, simulatedEntityAccess, entityRepository, nftRepository, tokenRepository);
        this.worldState = worldState;
        this.simulatedAliasManager = simulatedAliasManager;
        this.entityAccess = simulatedEntityAccess;
        this.entityRepository = entityRepository;
        this.nftRepository = nftRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void countIdsAllocatedByStacked(final int n) {
        numAllocatedIds += n;
    }

    @Override
    public Address newContractAddress(final Address sponsorAddressOrAlias) {
        final var sponsor = simulatedAliasManager.resolveForEvm(sponsorAddressOrAlias);
        final var newAddress = worldState.newContractAddress(sponsor);
        numAllocatedIds++;
        return newAddress;
    }

    @Override
    public Account get(final Address addressOrAlias) {
        final var address = simulatedAliasManager.resolveForEvm(addressOrAlias);
        if (isTokenRedirect(address)) {
            return new WorldStateTokenAccount(address);
        }
        return super.get(addressOrAlias);
    }

    @Override
    public EvmAccount getAccount(final Address addressOrAlias) {
        final var address = simulatedAliasManager.resolveForEvm(addressOrAlias);
        if (isTokenRedirect(address)) {
            final var proxyAccount = new WorldStateTokenAccount(address);
            final var newMutable =
                    new UpdateTrackingLedgerAccount<>(proxyAccount);
            return new WrappedEvmAccount(newMutable);
        }
        return super.getAccount(address);
    }

    /**
     * Returns the mirror form of the given EVM address if it exists; or 20 bytes of binary zeros if
     * the given address is the mirror address of an account with an EIP-1014 address.
     *
     * @param evmAddress an EVM address
     * @return its mirror form, or binary zeros if an EIP-1014 address should have been used for
     *     this account
     */
    public byte[] unaliased(final byte[] evmAddress) {
        final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
        if (!addressOrAlias.equals(canonicalAddress(addressOrAlias))) {
            return NON_CANONICAL_REFERENCE;
        }
        return simulatedAliasManager.resolveForEvm(addressOrAlias).toArrayUnsafe();
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
    public void commit() {
        super.commit();
        final var wrappedUpdater = ((HederaWorldUpdater) wrappedWorldView());
        wrappedUpdater.addSbhRefund(sbhRefund);
        wrappedUpdater.countIdsAllocatedByStacked(numAllocatedIds);
        sbhRefund = 0L;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public WorldUpdater updater() {
        return new SimulatedStackedWorldStateUpdater(
                (AbstractLedgerWorldUpdater) this,
                worldState,
                simulatedAliasManager,
                entityAccess, entityRepository,
                nftRepository, tokenRepository);
    }

    // --- Internal helpers
    boolean isTokenRedirect(final Address address) {
        return entityAccess.isTokenAccount(address);
    }
}
