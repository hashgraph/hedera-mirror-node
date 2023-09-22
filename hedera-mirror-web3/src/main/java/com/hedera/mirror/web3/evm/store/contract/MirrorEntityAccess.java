/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class MirrorEntityAccess implements HederaEvmEntityAccess {
    private final ContractStateRepository contractStateRepository;
    private final ContractRepository contractRepository;
    private final Store store;

    // An account is usable if it isn't deleted, if it has a balance >0, or if it has balance==0
    // but is not the 0-address or the empty account.  (This allows the special case where
    // a synthetic 0-address account is used in eth_estimateGas.)
    @SuppressWarnings("java:S1126") // "replace this if-then-else statement by a single return"
    // ^ Complaining about the final if- before the `return true`. But it is clear that this
    // method is a ladder of tests and merging the final `return true` into the last if- would
    // impair readability, in this case.
    @Override
    public boolean isUsable(final Address address) {
        // Do not consider expiry/renewal at this time.  It is not enabled in the network.
        // When it is handled it must be gated on (already existing) mirror node feature flags
        // (properties).

        final var account = store.getAccount(address, OnMissing.DONT_THROW);

        final var balance = account.getBalance();
        final var isDeleted = account.isDeleted();

        if (isDeleted) {
            return false;
        }

        if (balance > 0) {
            return true;
        }

        if (balance < 0) {
            return false;
        }

        if (Address.ZERO.equals(address)) {
            return false;
        }

        if (account.isEmptyAccount()) {
            return false;
        }

        return true;
    }

    @Override
    public long getBalance(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        if (account.isEmptyAccount()) {
            return 0L;
        }
        return account.getBalance();
    }

    @Override
    public boolean isExtant(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        return !account.isEmptyAccount();
    }

    @Override
    public boolean isTokenAccount(final Address address) {
        return !store.getToken(address, OnMissing.DONT_THROW).isEmptyToken();
    }

    @Override
    public ByteString alias(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        if (!account.isEmptyAccount()) {
            return account.getAlias();
        }
        return ByteString.EMPTY;
    }

    @Override
    public Bytes getStorage(final Address address, final Bytes key) {
        final var entityId = fetchEntityId(address);

        if (entityId == 0L) {
            return Bytes.EMPTY;
        }
        final var storage = contractStateRepository.findStorage(entityId, key.toArrayUnsafe());

        return storage.map(Bytes::wrap).orElse(Bytes.EMPTY);
    }

    @Override
    public Bytes fetchCodeIfPresent(final Address address) {
        final var entityId = fetchEntityId(address);

        if (entityId == 0) {
            return Bytes.EMPTY;
        }

        final var runtimeCode = contractRepository.findRuntimeBytecode(entityId);
        return runtimeCode.map(Bytes::wrap).orElse(Bytes.EMPTY);
    }

    private Long fetchEntityId(final Address address) {
        if (isMirror(address.toArrayUnsafe())) {
            return entityIdNumFromEvmAddress(address);
        }
        var entityId = store.getAccount(address, OnMissing.DONT_THROW).getEntityId();
        if (entityId == 0L) {
            entityId = store.getToken(address, OnMissing.DONT_THROW).getEntityId();
        }
        return entityId;
    }
}
