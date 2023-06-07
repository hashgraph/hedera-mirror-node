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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class MirrorEvmContractAliases extends HederaEvmContractAliases {
    final Map<Address, Address> aliases = new HashMap<>();
    private final MirrorEntityAccess mirrorEntityAccess;

    @Override
    public Address resolveForEvm(Address addressOrAlias) {
        // returning the zero address in cases when estimating contract creations
        if (addressOrAlias.equals(Address.ZERO)) {
            return addressOrAlias;
        }

        if (aliases.containsKey(addressOrAlias)) {
            return aliases.get(addressOrAlias);
        }

        final var entity = mirrorEntityAccess
                .findEntity(addressOrAlias)
                .orElseThrow(() -> new EntityNotFoundException("No such contract or token: " + addressOrAlias));

        final var entityId = entity.toEntityId();

        if (entity.getType() == EntityType.TOKEN) {
            final var bytes = Bytes.wrap(toEvmAddress(entityId));
            return Address.wrap(bytes);
        } else if (entity.getType() == EntityType.CONTRACT) {
            final var bytes =
                    Bytes.wrap(entity.getEvmAddress() != null ? entity.getEvmAddress() : toEvmAddress(entityId));
            return Address.wrap(bytes);
        } else {
            throw new InvalidParametersException("Not a contract or token: " + addressOrAlias);
        }
    }

    public void link(final Address alias, final Address address) {
        aliases.put(alias, address);
    }

    public void unlink(Address alias) {
        aliases.remove(alias);
    }
}
