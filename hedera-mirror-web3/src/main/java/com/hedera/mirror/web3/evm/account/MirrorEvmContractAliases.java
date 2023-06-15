/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.account;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class MirrorEvmContractAliases extends HederaEvmContractAliases {
    final Map<Address, Address> aliases = new HashMap<>();
    final Map<Address, Address> pendingAliases = new HashMap<>();
    final Set<Address> pendingRemovals = new HashSet<>();

    private final MirrorEntityAccess mirrorEntityAccess;

    @Override
    public Address resolveForEvm(Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }

        return resolveFromAliases(addressOrAlias).orElseGet(() -> resolveFromEntityAccess(addressOrAlias));
    }

    private Optional<Address> resolveFromAliases(Address alias) {
        if (pendingAliases.containsKey(alias)) {
            return Optional.ofNullable(pendingAliases.get(alias));
        }
        if (aliases.containsKey(alias) && !pendingRemovals.contains(alias)) {
            return Optional.ofNullable(aliases.get(alias));
        }
        return Optional.empty();
    }

    private Address resolveFromEntityAccess(Address addressOrAlias) {
        final var entity = mirrorEntityAccess
                .findEntity(addressOrAlias)
                .orElseThrow(() -> new EntityNotFoundException("No such contract or token: " + addressOrAlias));

        if (entity.getType() != EntityType.TOKEN && entity.getType() != EntityType.CONTRACT) {
            throw new InvalidParametersException("Not a contract or token: " + addressOrAlias);
        }

        final var resolvedAddress = Address.wrap(Bytes.wrap(toEvmAddress(entity.toEntityId())));
        link(addressOrAlias, resolvedAddress);

        return resolvedAddress;
    }

    public boolean isInUse(final Address address) {
        return aliases.containsKey(address) && !pendingRemovals.contains(address)
                || pendingAliases.containsKey(address);
    }

    public void link(final Address alias, final Address address) {
        pendingAliases.put(alias, address);
        pendingRemovals.remove(alias);
    }

    public void unlink(Address alias) {
        pendingRemovals.add(alias);
        pendingAliases.remove(alias);
    }

    public void commit() {
        aliases.putAll(pendingAliases);
        aliases.keySet().removeAll(pendingRemovals);

        resetPendingChanges();
    }

    public void resetPendingChanges() {
        pendingAliases.clear();
        pendingRemovals.clear();
    }
}
