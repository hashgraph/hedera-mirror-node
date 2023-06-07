/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmStackedWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

public class HederaEvmStackedWorldStateUpdater
        extends AbstractEvmStackedLedgerUpdater<HederaEvmMutableWorldState, Account>
        implements HederaEvmStackedWorldUpdater {

    protected final HederaEvmEntityAccess hederaEvmEntityAccess;

    private static final byte[] NON_CANONICAL_REFERENCE = new byte[20];
    private final EvmProperties evmProperties;
    private final TokenAccessor tokenAccessor;
    private final StackedStateFrames<Object> stackedStateFrames;

    public HederaEvmStackedWorldStateUpdater(
            final AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account> updater,
            final AccountAccessor accountAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final TokenAccessor tokenAccessor,
            final EvmProperties evmProperties,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final StackedStateFrames<Object> stackedStateFrames) {
        super(
                updater,
                accountAccessor,
                tokenAccessor,
                hederaEvmEntityAccess,
                mirrorEvmContractAliases,
                stackedStateFrames);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.stackedStateFrames = stackedStateFrames;
        this.stackedStateFrames.push();
        this.tokenAccessor = tokenAccessor;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EvmAccount createAccount(Address address, long nonce, Wei balance) {
        persistInStackedStateFrames(address, nonce, balance);
        final UpdateTrackingAccount account = new UpdateTrackingAccount<>(address, null);
        account.setNonce(nonce);
        account.setBalance(balance);
        return new WrappedEvmAccount(track(account));
    }

    @Override
    public Account get(final Address address) {
        if (isTokenRedirect(address)) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        return super.get(address);
    }

    @Override
    public EvmAccount getAccount(final Address address) {
        if (isTokenRedirect(address)) {
            final var proxyAccount = new HederaEvmWorldStateTokenAccount(address);
            final var newMutable = new UpdateTrackingAccount<>(proxyAccount, null);
            return new WrappedEvmAccount(newMutable);
        }

        return super.getAccount(address);
    }

    private void persistInStackedStateFrames(Address address, long nonce, Wei balance) {
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        final var accountModel = new com.hedera.services.store.models.Account(
                Id.fromGrpcAccount(accountIdFromEvmAddress(address.toArrayUnsafe())),
                0L,
                balance.toLong(),
                false,
                0L,
                0L,
                null,
                0,
                Collections.emptySortedMap(),
                Collections.emptySortedMap(),
                Collections.emptySortedSet(),
                0,
                0,
                0,
                nonce);
        accountAccessor.set(address, accountModel);
    }

    /**
     * Returns the mirror form of the given EVM address.
     *
     * @param evmAddress an EVM address
     * @return its mirror form
     */
    public byte[] permissivelyUnaliased(final byte[] evmAddress) {
        return aliases().resolveForEvm(Address.wrap(Bytes.wrap(evmAddress))).toArrayUnsafe();
    }

    /**
     * Returns the mirror form of the given EVM address if it exists; or 20 bytes of binary zeros if
     * the given address is the mirror address of an account with an EIP-1014 address. We refer to canonicalAddress as the alias/evm based address value of a given account.
     *
     * @param evmAddress an EVM address
     * @return its mirror form, or binary zeros if an EIP-1014 address should have been used for
     *     this account
     */
    public byte[] unaliased(final byte[] evmAddress) {
        final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
        if (!addressOrAlias.equals(tokenAccessor.canonicalAddress(addressOrAlias))) {
            return NON_CANONICAL_REFERENCE;
        }
        return aliases().resolveForEvm(addressOrAlias).toArrayUnsafe();
    }

    private boolean isTokenRedirect(final Address address) {
        return hederaEvmEntityAccess.isTokenAccount(address) && evmProperties.isRedirectTokenCallsEnabled();
    }

    @Override
    public Address priorityAddress(Address addressOrAlias) {
        return accountAccessor.canonicalAddress(addressOrAlias);
    }

    @Override
    public Address newAliasedContractAddress(Address sponsor, Address alias) {
        throw new UnsupportedOperationException("CREATE2 operation is not supported yet.");
    }
}
