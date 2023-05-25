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

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.WorldStateAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class HederaEvmWorldState implements HederaEvmMutableWorldState {

    private final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final EvmProperties evmProperties;
    private final AbstractCodeCache abstractCodeCache;

    private final AccountAccessor accountAccessor;
    private final TokenAccessor tokenAccessor;
    private final StackedStateFrames<Object> stackedStateFrames;

    private final EntityAddressSequencer entityAddressSequencer;
    private final MirrorEvmContractAliases mirrorAliasManager;

    public HederaEvmWorldState(
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final EvmProperties evmProperties,
            final AbstractCodeCache abstractCodeCache,
            final AccountAccessor accountAccessor,
            final TokenAccessor tokenAccessor,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorAliasManager,
            final StackedStateFrames<Object> stackedStateFrames) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.abstractCodeCache = abstractCodeCache;
        this.accountAccessor = accountAccessor;
        this.tokenAccessor = tokenAccessor;
        this.entityAddressSequencer = entityAddressSequencer;
        this.stackedStateFrames = stackedStateFrames;
        stackedStateFrames.push();
        this.mirrorAliasManager = mirrorAliasManager;
    }

    public Account get(final Address address) {
        if (address == null) {
            return null;
        }
        if (hederaEvmEntityAccess.isTokenAccount(address) && evmProperties.isRedirectTokenCallsEnabled()) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        if (!hederaEvmEntityAccess.isUsable(address)) {
            return null;
        }
        final long balance = hederaEvmEntityAccess.getBalance(address);
        return new WorldStateAccount(address, Wei.of(balance), abstractCodeCache, hederaEvmEntityAccess);
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
    public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HederaEvmWorldUpdater updater() {
        return new Updater(
                this,
                accountAccessor,
                hederaEvmEntityAccess,
                tokenAccessor,
                evmProperties,
                entityAddressSequencer,
                mirrorAliasManager,
                stackedStateFrames);
    }

    public static class Updater extends AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account>
            implements HederaEvmWorldUpdater {
        private final HederaEvmEntityAccess hederaEvmEntityAccess;
        private final TokenAccessor tokenAccessor;
        private final EvmProperties evmProperties;
        private final EntityAddressSequencer entityAddressSequencer;
        private final StackedStateFrames<Object> stackedStateFrames;

        private final MirrorEvmContractAliases mirrorAliasManager;

        protected Updater(
                final HederaEvmWorldState world,
                final AccountAccessor accountAccessor,
                final HederaEvmEntityAccess hederaEvmEntityAccess,
                final TokenAccessor tokenAccessor,
                final EvmProperties evmProperties,
                final EntityAddressSequencer contractAddressState,
                final MirrorEvmContractAliases mirrorAliasManager,
                final StackedStateFrames<Object> stackedStateFrames) {
            super(world, accountAccessor);
            this.tokenAccessor = tokenAccessor;
            this.hederaEvmEntityAccess = hederaEvmEntityAccess;
            this.evmProperties = evmProperties;
            this.entityAddressSequencer = contractAddressState;
            this.stackedStateFrames = stackedStateFrames;
            this.mirrorAliasManager = mirrorAliasManager;
        }

        @Override
        public Address newContractAddress(Address address) {
            return asTypedEvmAddress(entityAddressSequencer.getNewContractId(address));
        }

        @Override
        public long getSbhRefund() {
            return 0;
        }

        @Override
        public Account getForMutation(final Address address) {
            final HederaEvmWorldState wrapped = (HederaEvmWorldState) wrappedWorldView();
            return wrapped.get(address);
        }

        @Override
        public void commit() {
            final var topFrame = stackedStateFrames.top();
            if (stackedStateFrames.height() > 1) { // commit only to upstream RWCachingStateFrame
                topFrame.commit();
                stackedStateFrames.pop();
            }
        }

        @Override
        public WorldUpdater updater() {
            return new HederaEvmStackedWorldStateUpdater(
                    this, accountAccessor, hederaEvmEntityAccess, tokenAccessor, evmProperties, mirrorAliasManager, stackedStateFrames);
        }
    }

    @Override
    public void close() {
        // default no-op
    }
}
