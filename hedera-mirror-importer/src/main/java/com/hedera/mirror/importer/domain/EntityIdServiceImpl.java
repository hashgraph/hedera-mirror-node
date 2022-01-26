package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_ENTITY_ID;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.nio.ByteBuffer;
import java.util.Optional;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.common.domain.Aliasable;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.ContractRepository;

@Log4j2
@Named
public class EntityIdServiceImpl implements EntityIdService {

    private final Cache cache;
    private final ContractRepository contractRepository;

    public EntityIdServiceImpl(@Named(CACHE_MANAGER_ENTITY_ID) CacheManager cacheManager,
                               ContractRepository contractRepository) {
        this.cache = cacheManager.getCache("entityId");
        this.contractRepository = contractRepository;
    }

    /**
     * Looks up the domain EntityId of the protobuf contractID.
     *
     * @param contractId The protobuf contract id
     * @return The domain entityId object of the protobuf contract id
     */
    @Override
    public EntityId lookup(ContractID contractId) {
        if (contractId.equals(ContractID.getDefaultInstance())) {
            return EntityId.EMPTY;
        }

        int hashCode = contractId.hashCode();
        EntityId cached = cache.get(hashCode, EntityId.class);
        if (cached != null) {
            return cached;
        }

        var contractCase = contractId.getContractCase();
        EntityId entityId;
        switch (contractCase) {
            case CONTRACTNUM:
                entityId =  EntityId.of(contractId);
                break;
            case EVM_ADDRESS:
                entityId = lookupByEvmAddress(contractId);
                break;
            default:
                log.error("Invalid ContractID ContractCase {}", contractCase);
                throw new InvalidDatasetException(String.format("Invalid ContractID ContractCase %s", contractCase));
        }

        if (!EntityId.isEmpty(entityId)) {
            cache.put(hashCode, entityId);
        }

        return entityId;
    }

    @Override
    public void store(Aliasable aliasable) {
        if (aliasable.getDeleted() != null && aliasable.getDeleted()) {
            return;
        }

        if (aliasable.getAlias() == null) {
            return;
        }

        EntityId entityId = aliasable.toEntityId();
        ByteString alias = ByteString.copyFrom(aliasable.getAlias());
        int hashCode;
        if (aliasable.getType() == EntityType.ACCOUNT) {
            hashCode = AccountID.newBuilder()
                    .setShardNum(entityId.getShardNum())
                    .setRealmNum(entityId.getRealmNum())
                    .setAlias(alias)
                    .build()
                    .hashCode();
        } else {
            hashCode = ContractID.newBuilder()
                    .setShardNum(entityId.getShardNum())
                    .setRealmNum(entityId.getRealmNum())
                    .setEvmAddress(alias)
                    .build()
                    .hashCode();
        }

        cache.put(hashCode, entityId);
    }

    private EntityId lookupByEvmAddress(ContractID contractId) {
        byte[] evmAddress = DomainUtils.toBytes(contractId.getEvmAddress());
        return Optional.ofNullable(parseEvmAddress(evmAddress))
                // verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShardNum() == contractId.getShardNum() && e.getRealmNum() == contractId.getRealmNum())
                .or(() -> contractRepository.findByEvmAddress(evmAddress).map(id -> EntityId.of(id, CONTRACT)))
                .orElseGet(() -> {
                    log.warn("No match found for the evm address {}, it could be that the mirrornode has partial " +
                            "data or the address doesn't exist in network", () -> DomainUtils.bytesToHex(evmAddress));
                    return EntityId.EMPTY;
                });
    }

    private EntityId parseEvmAddress(byte[] evmAddress) {
        try {
            // the 'shard.realm.num' form evm address has 4 bytes for shard, and 8 bytes each for realm and num.
            ByteBuffer buffer = ByteBuffer.wrap(evmAddress);
            return EntityId.of(buffer.getInt(), buffer.getLong(), buffer.getLong(), CONTRACT);
        } catch (InvalidEntityException ex) {
            log.debug("Failed to parse shard.realm.num form evm address into EntityId", ex);
            return null;
        }
    }
}
