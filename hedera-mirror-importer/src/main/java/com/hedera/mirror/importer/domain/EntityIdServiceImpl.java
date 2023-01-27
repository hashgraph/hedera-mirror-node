package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.repository.EntityRepository;

@Log4j2
@Named
public class EntityIdServiceImpl implements EntityIdService {

    private final Cache cache;
    private final EntityRepository entityRepository;

    public EntityIdServiceImpl(@Named(CACHE_MANAGER_ALIAS) CacheManager cacheManager,
                               EntityRepository entityRepository) {
        this.cache = cacheManager.getCache("entityId");
        this.entityRepository = entityRepository;
    }

    @Override
    public EntityId lookup(AccountID accountId) {
        return doLookup(accountId, () -> load(accountId));
    }

    @Override
    public EntityId lookup(AliasNotFoundAction action, AccountID... accountIds) {
        return doLookups(action, accountIds, this::load);
    }

    @Override
    public EntityId lookup(ContractID contractId) {
        return doLookup(contractId, () -> load(contractId));
    }

    @Override
    public EntityId lookup(AliasNotFoundAction action, ContractID... contractIds) {
        return doLookups(action, contractIds, this::load);
    }

    @SneakyThrows
    private EntityId doLookup(GeneratedMessageV3 entityIdProto, Callable<EntityId> loader) {
        if (entityIdProto == null || entityIdProto.equals(entityIdProto.getDefaultInstanceForType())) {
            return EntityId.EMPTY;
        }

        try {
            return cache.get(entityIdProto, loader);
        } catch (Cache.ValueRetrievalException e) {
            throw e.getCause();
        }
    }

    private <T extends GeneratedMessageV3> EntityId doLookups(AliasNotFoundAction action, T[] entityIdProtos,
                                                              Function<T, EntityId> loader) {
        for (T entityIdProto : entityIdProtos) {
            try {
                var entityId = doLookup(entityIdProto, () -> loader.apply(entityIdProto));
                if (!EntityId.isEmpty(entityId)) {
                    return entityId;
                }
            } catch (Exception e) {
                if (e instanceof AliasNotFoundException && action == AliasNotFoundAction.ERROR) {
                    throw e;
                }
                log.warn("Skipping entity ID {}: {}", entityIdProto, e.getMessage());
            }
        }
        return EntityId.EMPTY;
    }

    @Override
    public void notify(Entity entity) {
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted())) {
            return;
        }

        byte[] aliasBytes = entity.getAlias() != null ? entity.getAlias() : entity.getEvmAddress();
        ByteString alias = DomainUtils.fromBytes(aliasBytes);
        if (alias == null) {
            return;
        }

        EntityId entityId = entity.toEntityId();
        EntityType type = entity.getType();
        GeneratedMessageV3.Builder<?> builder;

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

        cache.put(builder.build(), entityId);
    }

    private EntityId load(AccountID accountId) {
        switch (accountId.getAccountCase()) {
            case ACCOUNTNUM:
                return EntityId.of(accountId);
            case ALIAS:
                byte[] alias = DomainUtils.toBytes(accountId.getAlias());
                return alias.length == DomainUtils.EVM_ADDRESS_LENGTH ?
                        findByEvmAddress(alias, accountId.getShardNum(), accountId.getRealmNum(), ACCOUNT) :
                        entityRepository.findByAlias(alias)
                                .map(id -> EntityId.of(id, ACCOUNT))
                                .orElseThrow(() -> new AliasNotFoundException(Hex.encodeHexString(alias), ACCOUNT));
            case EVM_ADDRESS:
                byte[] evmAddress = DomainUtils.toBytes(accountId.getEvmAddress());
                return findByEvmAddress(evmAddress, accountId.getShardNum(), accountId.getRealmNum(), ACCOUNT);
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
                byte[] evmAddress = DomainUtils.toBytes(contractId.getEvmAddress());
                return findByEvmAddress(evmAddress, contractId.getShardNum(), contractId.getRealmNum(), CONTRACT);
            default:
                throw new InvalidDatasetException("Invalid ContractID: " + contractId);
        }
    }

    private EntityId findByEvmAddress(byte[] evmAddress, long shardNum, long realmNum, EntityType type) {
        return Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                // Verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShardNum() == shardNum && e.getRealmNum() == realmNum)
                .or(() -> entityRepository.findByEvmAddress(evmAddress).map(id -> EntityId.of(id, type)))
                .orElseThrow(() -> new AliasNotFoundException(Hex.encodeHexString(evmAddress), type));
    }
}
