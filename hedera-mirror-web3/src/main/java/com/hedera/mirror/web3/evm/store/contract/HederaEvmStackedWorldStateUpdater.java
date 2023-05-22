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

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

public class HederaEvmStackedWorldStateUpdater
        extends AbstractEvmStackedLedgerUpdater<HederaEvmMutableWorldState, Account> {

    protected final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final StackedStateFrames<Object> stackedStateFrames;
    private final EvmProperties evmProperties;

    public HederaEvmStackedWorldStateUpdater(
            final AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account> updater,
            final AccountAccessor accountAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final TokenAccessor tokenAccessor,
            final EvmProperties evmProperties,
            final StackedStateFrames<Object> stackedStateFrames) {
        super(updater, accountAccessor, tokenAccessor, hederaEvmEntityAccess);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.stackedStateFrames = stackedStateFrames;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EvmAccount createAccount(Address address, long nonce, Wei balance) {
        persistInStackedStateFrames(address, balance);
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

    private boolean isTokenRedirect(final Address address) {
        return hederaEvmEntityAccess.isTokenAccount(address) && evmProperties.isRedirectTokenCallsEnabled();
    }

    private void persistInStackedStateFrames(Address address, Wei balance) {
        // create new RWCachingStateFrame
        stackedStateFrames.push();
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        final var accountModel = new com.hedera.services.store.models.Account(
                Id.fromGrpcAccount(accountIdFromEvmAddress(address.toArrayUnsafe())),
                0,
                balance.toLong(),
                false,
                0,
                0,
                null,
                0,
                Collections.emptySortedMap(),
                Collections.emptySortedMap(),
                Collections.emptySortedSet(),
                0,
                0,
                0);
        accountAccessor.set(address, accountModel);
        // if the current frame's upstream is NOT ROCachingStateFrame - commit and pop
        if (stackedStateFrames.height() > 1) {
            topFrame.commit();
            stackedStateFrames.pop();
        }
    }
}
