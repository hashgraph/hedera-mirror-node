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

import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class MirrorEvmContractAliases extends HederaEvmContractAliases {
    final Map<Address, Address> aliases = new HashMap<>();
    final Map<Address, Address> pendingChanges = new HashMap<>();
    private final MirrorEntityAccess mirrorEntityAccess;

    @Override
    public Address resolveForEvm(Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }

        if (pendingChanges.containsKey(addressOrAlias)) {
            return pendingChanges.get(addressOrAlias);
        }

        if (aliases.containsKey(addressOrAlias)) {
            return aliases.get(addressOrAlias);
        }

        final var entity = mirrorEntityAccess
                .findEntity(addressOrAlias)
                .orElseThrow(() -> new EntityNotFoundException("No such contract or token: " + addressOrAlias));

        final var entityId = entity.toEntityId();
        return Address.wrap(Bytes.wrap(toEvmAddress(entityId)));
    }

    public boolean isInUse(final Address address) {
        return pendingChanges.containsKey(address) || aliases.containsKey(address);
    }

    public void link(final Address alias, final Address address) {
        pendingChanges.put(alias, address);
    }

    public void unlink(Address alias) {
        pendingChanges.remove(alias);
    }

    public void commit() {
        aliases.putAll(pendingChanges);
    }

    public void resetPendingChanges() {
        pendingChanges.clear();
    }
}
