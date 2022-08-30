package com.hedera.services.transaction.store.contracts;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.evm.SimulatedEntityAccess;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;

/**
 * Base implementation of a {@link org.hyperledger.besu.evm.worldstate.WorldUpdater} that buffers a
 * set of EVM account mutations (updates and deletions) that are mirrored in the accounts ledger of
 * a "parallel" {WorldLedgers} instance.
 *
 * <p>We need both representations of the change-set because the Besu EVM is implemented in terms of
 * the {@link Account} type hierarchy, while the logic for the native HTS pre-compiles is
 * implemented in terms of {com.hedera.services.ledger.TransactionalLedger} instances.
 *
 * @param <W> the type of the wrapped world view
 * @param <A> the account specialization used by the wrapped world view
 */
public abstract class AbstractStackedLedgerUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerWorldUpdater<
                AbstractLedgerWorldUpdater<W, A>, UpdateTrackingLedgerAccount<A>> {

    protected AbstractStackedLedgerUpdater(
            final AbstractLedgerWorldUpdater<W, A> world,
            final SimulatedAliasManager simulatedAliasManager,
            final SimulatedEntityAccess simulatedEntityAccess,
            final EntityRepository entityRepository,
            final NftRepository nftRepository,
            final TokenRepository tokenRepository) {
        super(world, simulatedAliasManager, simulatedEntityAccess, entityRepository, nftRepository, tokenRepository);
    }

    /** {@inheritDoc} */
    @Override
    protected UpdateTrackingLedgerAccount<A> getForMutation(final Address address) {
        final var wrapped = wrappedWorldView();
        final var wrappedMutable = wrapped.updatedAccounts.get(address);
        if (wrappedMutable != null) {
            return wrappedMutable;
        }
        if (wrapped.deletedAccounts.contains(address)) {
            return null;
        }
        final A account = wrapped.getForMutation(address);
        return account == null ? null : new UpdateTrackingLedgerAccount<>(account);
    }

    /** {@inheritDoc} */
    @Override
    public void commit() {
        final var wrapped = wrappedWorldView();

        /* NOTE: In a traditional Ethereum context, it is possible with use of CREATE2 for a stacked updater
         * to re-create the very same account that was deleted by its parent updater. But since every Hedera
         * account gets a unique 0.0.X id---and corresponding unique mirror 0x0...0X address---that is not
         * possible here. So we needn't remove our updated accounts from our parent's deleted accounts. */
        getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
        wrapped.deletedAccounts.addAll(getDeletedAccounts());

        for (final var updatedAccount : getUpdatedAccounts()) {
            //            /* First check if there is already a mutable tracker for this account in
            // our parent updater;
            //             * if there is, we commit by propagating the changes from our tracker into
            // that tracker. */
            var mutable = wrapped.updatedAccounts.get(updatedAccount.getAddress());
            if (mutable == null) {
                /* If the parent updater didn't have a mutable tracker, we must give it one. Unless
                 * we created this account (meaning our mutable tracker has no wrapped account), the
                 * "inner" mutable tracker we created in getForMutation() will do fine; we just need to
                 * update its tracking accounts to the parent's. */
                mutable = updatedAccount.getWrappedAccount();
                if (mutable == null) {
                    /* We created this account, so create a new tracker for our parent. */
                    mutable = new UpdateTrackingLedgerAccount<>(updatedAccount.getAddress());
                }
                wrapped.updatedAccounts.put(mutable.getAddress(), mutable);
            }
            mutable.setNonce(updatedAccount.getNonce());

            if (!updatedAccount.wrappedAccountIsTokenProxy()) {
                mutable.setBalance(updatedAccount.getBalance());
            }
            if (updatedAccount.codeWasUpdated()) {
                mutable.setCode(updatedAccount.getCode());
            }
            if (updatedAccount.getStorageWasCleared()) {
                mutable.clearStorage();
            }
            updatedAccount.getUpdatedStorage().forEach(mutable::setStorageValue);
        }
    }
}
