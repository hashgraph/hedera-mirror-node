package com.hedera.mirror.web3.evm.store.contract;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromBytes;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.services.evm.accounts.HederaEvmContractAliases.isMirror;

import com.google.protobuf.ByteString;
import java.util.Optional;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.services.evm.store.contracts.HederaEvmEntityAccess;

@Named
@RequiredArgsConstructor
public class MirrorEntityAccess implements HederaEvmEntityAccess {
    private final EntityRepository entityRepository;
    private final ContractRepository contractRepository;
    private final ContractStateRepository contractStateRepository;

    @Override
    public boolean isUsable(Address address) {
        return findEntity(address).filter(e -> e.getBalance() > 0).isPresent();
    }

    @Override
    public long getBalance(Address address) {
        final var entity = findEntity(address);
        return entity.map(Entity::getBalance).orElse(0L);
    }

    @Override
    public boolean isExtant(Address address) {
        return entityRepository.existsById(entityIdFromEvmAddress(address));
    }

    @Override
    public boolean isTokenAccount(Address address) {
        return findEntity(address).filter(e -> e.getType() == TOKEN).isPresent();
    }

    @Override
    public ByteString alias(Address address) {
        final var entity = findEntity(address);
        return entity.map(value -> fromBytes(value.getAlias())).orElse(ByteString.EMPTY);
    }

    @Override
    public UInt256 getStorage(Address address, Bytes key) {
        final var storage = contractStateRepository.findStorage(entityIdFromEvmAddress(address),
                key.toArrayUnsafe());
        final var storageBytes = storage.map(Bytes::wrap).orElse(Bytes.EMPTY);
        return UInt256.fromBytes(storageBytes);
    }

    @Override
    public Bytes fetchCodeIfPresent(Address address) {
        final var runtimeCode = contractRepository.findRuntimeBytecode(entityIdFromEvmAddress(address));
        return runtimeCode.map(Bytes::wrap).orElse(Bytes.EMPTY);
    }

    private Optional<Entity> findEntity(Address address) {
        final var addressBytes = address.toArrayUnsafe();
        if (isMirror(addressBytes)) {
            final var entityId = entityIdFromEvmAddress(address);
            return entityRepository.findByIdAndDeletedIsFalse(entityId);
        } else {
            return entityRepository.findByAliasAndDeletedIsFalse(addressBytes);
        }
    }

    private Long entityIdFromEvmAddress(Address address) {
        final var id = fromEvmAddress(address.toArrayUnsafe());
        return id.getId();
    }
}
