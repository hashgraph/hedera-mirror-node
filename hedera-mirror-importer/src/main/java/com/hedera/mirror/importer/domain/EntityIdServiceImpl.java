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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_ALIAS;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
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
import com.hedera.mirror.importer.repository.EntityRepository;

@Log4j2
@Named
public class EntityIdServiceImpl implements EntityIdService {

    private final Cache cache;
    private final ContractRepository contractRepository;
    private final EntityRepository entityRepository;

    public EntityIdServiceImpl(@Named(CACHE_MANAGER_ALIAS) CacheManager cacheManager,
                               ContractRepository contractRepository, EntityRepository entityRepository) {
        this.cache = cacheManager.getCache("entityId");
        this.contractRepository = contractRepository;
        this.entityRepository = entityRepository;
    }

    /**
     * Looks up the domain EntityId of the protobuf accountId.
     *
     * @param accountId The protobuf account id
     * @return The domain entityId object of the protobuf account id
     */
    @Override
    public EntityId lookup(AccountID accountId) {
        return lookupGeneral(accountId);
    }

    /**
     * Looks up the domain EntityId of the protobuf contractID.
     *
     * @param contractId The protobuf contract id
     * @return The domain entityId object of the protobuf contract id
     */
    @Override
    public EntityId lookup(ContractID contractId) {
        return lookupGeneral(contractId);
    }

    @Override
    public void store(Aliasable aliasable) {
        if (aliasable.getDeleted() != null && aliasable.getDeleted()) {
            return;
        }

        ByteString alias = DomainUtils.fromBytes(aliasable.getAlias());
        if (alias == null) {
            return;
        }

        int hashCode;
        EntityId entityId = aliasable.toEntityId();
        EntityType type = aliasable.getType();
        switch (type) {
            case ACCOUNT:
                hashCode = AccountID.newBuilder()
                        .setShardNum(entityId.getShardNum())
                        .setRealmNum(entityId.getRealmNum())
                        .setAlias(alias)
                        .build()
                        .hashCode();
                break;
            case CONTRACT:
                hashCode = ContractID.newBuilder()
                        .setShardNum(entityId.getShardNum())
                        .setRealmNum(entityId.getRealmNum())
                        .setEvmAddress(alias)
                        .build()
                        .hashCode();
                break;
            default:
                throw new InvalidEntityException(String.format("%s entity can't have alias", type));
        }

        cache.put(hashCode, entityId);
    }

    private EntityId lookupGeneral(GeneratedMessageV3 protoEntityId) {
        if (protoEntityId == null || protoEntityId.equals(protoEntityId.getDefaultInstanceForType())) {
            return EntityId.EMPTY;
        }

        int hashCode = protoEntityId.hashCode();
        EntityId cached = cache.get(hashCode, EntityId.class);
        if (cached != null) {
            return cached;
        }

        EntityId entityId;
        if (protoEntityId instanceof AccountID) {
            entityId = lookupAccountId((AccountID) protoEntityId);
        } else if (protoEntityId instanceof ContractID) {
            entityId = lookupContractId((ContractID) protoEntityId);
        } else {
            String message = String.format("Entity type %s not supported", protoEntityId.getClass().getSimpleName());
            throw new InvalidEntityException(message);
        }

        if (!EntityId.isEmpty(entityId)) {
            cache.put(hashCode, entityId);
        }

        return entityId;
    }

    private EntityId lookupAccountId(AccountID accountId) {
        var accountCase = accountId.getAccountCase();
        switch (accountCase) {
            case ACCOUNTNUM:
                return EntityId.of(accountId);
            case ALIAS:
                byte[] alias = DomainUtils.toBytes(accountId.getAlias());
                return entityRepository.findByAlias(alias)
                        .map(id -> EntityId.of(id, ACCOUNT))
                        .orElseGet(() -> {
                            log.warn("No match found for the alias {}, it could be that the mirrornode has partial " +
                                    "data or the alias doesn't exist in network", () -> DomainUtils.bytesToHex(alias));
                            return EntityId.EMPTY;
                        });
            default:
                throw new InvalidDatasetException(String.format("Invalid AccountID AccountCase %s", accountCase));
        }
    }

    @SuppressWarnings("deprecation")
    private EntityId lookupContractId(ContractID contractId) {
        var contractCase = contractId.getContractCase();
        switch (contractCase) {
            case CONTRACTNUM:
                return EntityId.of(contractId);
            case EVM_ADDRESS:
                return lookupByEvmAddress(contractId);
            default:
                throw new InvalidDatasetException(String.format("Invalid ContractID ContractCase %s", contractCase));
        }
    }

    private EntityId lookupByEvmAddress(ContractID contractId) {
        byte[] evmAddress = DomainUtils.toBytes(contractId.getEvmAddress());
        return Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                // verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShardNum() == contractId.getShardNum() && e.getRealmNum() == contractId.getRealmNum())
                .or(() -> contractRepository.findByEvmAddress(evmAddress).map(id -> EntityId.of(id, CONTRACT)))
                .orElseGet(() -> {
                    log.warn("No match found for the evm address {}, it could be that the mirrornode has partial " +
                            "data or the address doesn't exist in network", () -> DomainUtils.bytesToHex(evmAddress));
                    return EntityId.EMPTY;
                });
    }
}
