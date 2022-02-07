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
import java.util.concurrent.Callable;
import java.util.function.Function;
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

    @Override
    public EntityId lookup(AccountID accountId) {
        return doLookup(accountId, () -> load(accountId));
    }

    @Override
    public EntityId lookup(AccountID... accountIds) {
        return doLookups(accountIds, this::load);
    }

    @Override
    public EntityId lookup(ContractID contractId) {
        return doLookup(contractId, () -> load(contractId));
    }

    @Override
    public EntityId lookup(ContractID... contractIds) {
        return doLookups(contractIds, this::load);
    }

    private EntityId doLookup(GeneratedMessageV3 entityIdProto, Callable<EntityId> loader) {
        if (entityIdProto == null || entityIdProto.equals(entityIdProto.getDefaultInstanceForType())) {
            return EntityId.EMPTY;
        }

        EntityId entityId = cache.get(entityIdProto.hashCode(), loader);

        if (entityId == null) {
            log.warn("No match found. It could be that the mirror node has partial data " +
                    "or the alias doesn't exist: {}", entityIdProto);
            return EntityId.EMPTY;
        }

        return entityId;
    }

    private <T extends GeneratedMessageV3> EntityId doLookups(T[] entityIdProtos, Function<T, EntityId> loader) {
        for (T entityIdProto : entityIdProtos) {
            try {
                EntityId entityId = doLookup(entityIdProto, () -> loader.apply(entityIdProto));
                if (!EntityId.isEmpty(entityId)) {
                    return entityId;
                }
            } catch (Exception e) {
                log.warn("Skipping entity ID {}: {}", entityIdProto, e.getMessage());
            }
        }
        return EntityId.EMPTY;
    }

    @Override
    public void notify(Aliasable aliasable) {
        if (aliasable == null || (aliasable.getDeleted() != null && aliasable.getDeleted())) {
            return;
        }

        ByteString alias = DomainUtils.fromBytes(aliasable.getAlias());
        if (alias == null) {
            return;
        }

        EntityId entityId = aliasable.toEntityId();
        EntityType type = aliasable.getType();
        GeneratedMessageV3.Builder builder;

        switch (type) {
            case ACCOUNT:
                builder = AccountID.newBuilder()
                        .setShardNum(entityId.getShardNum())
                        .setRealmNum(entityId.getRealmNum())
                        .setAlias(alias);
                break;
            case CONTRACT:
                builder = ContractID.newBuilder()
                        .setShardNum(entityId.getShardNum())
                        .setRealmNum(entityId.getRealmNum())
                        .setEvmAddress(alias);
                break;
            default:
                throw new InvalidEntityException(String.format("%s entity can't have alias", type));
        }

        cache.put(builder.build().hashCode(), entityId);
    }

    private EntityId load(AccountID accountId) {
        switch (accountId.getAccountCase()) {
            case ACCOUNTNUM:
                return EntityId.of(accountId);
            case ALIAS:
                byte[] alias = DomainUtils.toBytes(accountId.getAlias());
                return entityRepository.findByAlias(alias)
                        .map(id -> EntityId.of(id, ACCOUNT))
                        .orElse(null);
            default:
                throw new InvalidDatasetException("Invalid AccountID: " + accountId);
        }
    }

    @SuppressWarnings("deprecation")
    private EntityId load(ContractID contractId) {
        switch (contractId.getContractCase()) {
            case CONTRACTNUM:
                return EntityId.of(contractId);
            case EVM_ADDRESS:
                return findByEvmAddress(contractId);
            default:
                throw new InvalidDatasetException("Invalid ContractID: " + contractId);
        }
    }

    private EntityId findByEvmAddress(ContractID contractId) {
        byte[] evmAddress = DomainUtils.toBytes(contractId.getEvmAddress());
        return Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                // Verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShardNum() == contractId.getShardNum() && e.getRealmNum() == contractId.getRealmNum())
                .or(() -> contractRepository.findByEvmAddress(evmAddress).map(id -> EntityId.of(id, CONTRACT)))
                .orElse(null);
    }
}
