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

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.EntityRepository;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

@Named
@RequiredArgsConstructor
public class EntityDatabaseAccessor extends DatabaseAccessor<Object, Entity> {
    private final EntityRepository entityRepository;

    @Override
    public @NotNull Optional<Entity> get(@NotNull Object address) {
        var addressBytes = ((Address) address).toArrayUnsafe();
        if (isMirror(addressBytes)) {
            final var entityId = entityIdNumFromEvmAddress(((Address) address));
            return entityRepository.findByIdAndDeletedIsFalse(entityId);
        } else {
            return entityRepository.findByEvmAddressAndDeletedIsFalse(addressBytes);
        }
    }

    public Address evmAddressFromId(EntityId entityId) {
        Entity entity =
                entityRepository.findByIdAndDeletedIsFalse(entityId.getId()).orElse(null);

        if (entity == null) {
            return Address.ZERO;
        }

        if (entity.getEvmAddress() != null) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }

        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }

        return toAddress(entityId);
    }
}
