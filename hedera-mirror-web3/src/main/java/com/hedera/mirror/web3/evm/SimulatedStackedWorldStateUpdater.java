package com.hedera.mirror.web3.evm;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.transaction.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.transaction.store.contracts.AbstractStackedLedgerUpdater;
import com.hedera.services.transaction.store.contracts.HederaMutableWorldState;
import com.hedera.services.transaction.store.contracts.HederaWorldUpdater;
import com.hedera.services.transaction.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.services.transaction.store.contracts.WorldStateTokenAccount;

public class SimulatedStackedWorldStateUpdater
        extends AbstractStackedLedgerUpdater<HederaMutableWorldState, Account>
        implements HederaWorldUpdater {

    private final HederaMutableWorldState worldState;
    private final SimulatedAliasManager simulatedAliasManager;
    private final SimulatedEntityAccess entityAccess;
    private final EntityRepository entityRepository;

    private long sbhRefund = 0L;
    private int numAllocatedIds = 0;

    public SimulatedStackedWorldStateUpdater(
            final AbstractLedgerWorldUpdater<HederaMutableWorldState, Account> updater,
            final HederaMutableWorldState worldState,
            final SimulatedAliasManager simulatedAliasManager,
            final SimulatedEntityAccess simulatedEntityAccess,
            final EntityRepository entityRepository) {
        super(updater, simulatedAliasManager, simulatedEntityAccess, entityRepository);
        this.worldState = worldState;
        this.simulatedAliasManager = simulatedAliasManager;
        this.entityAccess = simulatedEntityAccess;
        this.entityRepository = entityRepository;
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
                entityAccess, entityRepository);
    }

    // --- Internal helpers
    //FUTURE WORK to be implemented
    boolean isTokenRedirect(final Address address) {
        return entityAccess.isTokenAccount(address);
    }
}
