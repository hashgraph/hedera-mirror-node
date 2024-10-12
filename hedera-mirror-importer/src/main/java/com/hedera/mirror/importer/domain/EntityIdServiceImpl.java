/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.domain;

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.common.util.DomainUtils.fromBytes;
import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_ALIAS;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static com.hedera.mirror.importer.util.Utility.aliasToEvmAddress;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@CustomLog
@Named
public class EntityIdServiceImpl implements EntityIdService {

    private static final Optional<EntityId> EMPTY = Optional.of(EntityId.EMPTY);

    private final Cache cache;
    private final EntityRepository entityRepository;

    public EntityIdServiceImpl(@Qualifier(CACHE_ALIAS) CacheManager cacheManager, EntityRepository entityRepository) {
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.entityRepository = entityRepository;
    }

    @Override
    public Optional<EntityId> lookup(AccountID accountId) {
        if (accountId == null || accountId.equals(AccountID.getDefaultInstance())) {
            return EMPTY;
        }

        return switch (accountId.getAccountCase()) {
            case ACCOUNTNUM -> Optional.ofNullable(EntityId.of(accountId));
            case ALIAS -> {
                long shard = accountId.getShardNum();
                long realm = accountId.getRealmNum();
                byte[] alias = toBytes(accountId.getAlias());

                yield cacheLookup(
                                accountId.getAlias(),
                                () -> alias.length == EVM_ADDRESS_LENGTH
                                        ? findByEvmAddress(alias, shard, realm)
                                        : entityRepository.findByAlias(alias).map(EntityId::of))
                        .or(() -> {
                            if (alias.length == EVM_ADDRESS_LENGTH) {
                                return Optional.empty();
                            }
                            var evmAddress = aliasToEvmAddress(alias);
                            return cacheLookup(fromBytes(evmAddress), () -> findByEvmAddress(evmAddress, shard, realm));
                        });
            }
            default -> {
                Utility.handleRecoverableError(
                        "Invalid Account Case for AccountID {}: {}", accountId, accountId.getAccountCase());
                yield Optional.empty();
            }
        };
    }

    @Override
    public Optional<EntityId> lookup(AccountID... accountIds) {
        return doLookups(accountIds, this::lookup);
    }

    @Override
    public Optional<EntityId> lookup(ContractID contractId) {
        return lookup(contractId, true);
    }

    @Override
    public Optional<EntityId> lookup(ContractID contractId, boolean throwRecoverableError) {
        if (contractId == null || contractId.equals(ContractID.getDefaultInstance())) {
            return EMPTY;
        }

        return switch (contractId.getContractCase()) {
            case CONTRACTNUM -> Optional.ofNullable(EntityId.of(contractId));
            case EVM_ADDRESS -> cacheLookup(
                    contractId.getEvmAddress(),
                    () -> findByEvmAddress(
                            toBytes(contractId.getEvmAddress()),
                            contractId.getShardNum(),
                            contractId.getRealmNum(),
                            throwRecoverableError));
            default -> {
                Utility.handleRecoverableError("Invalid ContractID: {}", contractId);
                yield Optional.empty();
            }
        };
    }

    @Override
    public Optional<EntityId> lookup(ContractID... contractIds) {
        return doLookups(contractIds, this::lookup);
    }

    private Optional<EntityId> cacheLookup(ByteString key, Callable<Optional<EntityId>> loader) {
        try {
            if (key == null || key.equals(ByteString.EMPTY)) {
                return Optional.empty();
            }

            return cache.get(key, loader);
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up alias or EVM address {} from cache", key, e);
            return Optional.empty();
        }
    }

    private <T extends GeneratedMessageV3> Optional<EntityId> doLookups(
            T[] entityIdProtos, Function<T, Optional<EntityId>> loader) {
        for (T entityIdProto : entityIdProtos) {
            var entityId = loader.apply(entityIdProto);
            if (!entityId.isEmpty() && !EntityId.isEmpty(entityId.get())) {
                return entityId;
            }
        }
        return EMPTY;
    }

    @Override
    public void notify(Entity entity) {
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted())) {
            return;
        }

        byte[] aliasBytes = entity.getAlias() != null ? entity.getAlias() : entity.getEvmAddress();
        if (aliasBytes == null) {
            return;
        }

        var alias = DomainUtils.fromBytes(aliasBytes);
        var entityId = Optional.ofNullable(entity.toEntityId());
        EntityType type = entity.getType();

        switch (type) {
            case ACCOUNT -> {
                cache.put(alias, entityId);

                // Accounts can have an alias and an EVM address so warm the cache with both
                if (entity.getAlias() != null && entity.getEvmAddress() != null) {
                    cache.put(fromBytes(entity.getEvmAddress()), entityId);
                }
            }
            case CONTRACT -> cache.put(alias, entityId);
            default -> Utility.handleRecoverableError("Invalid Entity: {} entity can't have alias", type);
        }
    }

    private Optional<EntityId> findByEvmAddress(byte[] evmAddress, long shardNum, long realmNum) {
        return findByEvmAddress(evmAddress, shardNum, realmNum, true);
    }

    private Optional<EntityId> findByEvmAddress(
            byte[] evmAddress, long shardNum, long realmNum, boolean throwRecoverableError) {
        var id = Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                // Verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShard() == shardNum && e.getRealm() == realmNum)
                .or(() -> entityRepository.findByEvmAddress(evmAddress).map(EntityId::of));

        if (id.isEmpty() && throwRecoverableError) {
            Utility.handleRecoverableError("Entity not found for EVM address {}", Hex.encodeHexString(evmAddress));
        }

        return id;
    }
}
