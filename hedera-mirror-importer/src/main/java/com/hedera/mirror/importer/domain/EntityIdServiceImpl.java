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
            case ALIAS -> cacheLookup(accountId.getAlias(), () -> {
                byte[] alias = toBytes(accountId.getAlias());
                return alias.length == DomainUtils.EVM_ADDRESS_LENGTH
                        ? findByEvmAddress(alias, accountId.getShardNum(), accountId.getRealmNum())
                        : findByPublicKeyAlias(alias, accountId.getShardNum(), accountId.getRealmNum());
            });

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
        if (contractId == null || contractId.equals(ContractID.getDefaultInstance())) {
            return EMPTY;
        }

        return switch (contractId.getContractCase()) {
            case CONTRACTNUM -> Optional.ofNullable(EntityId.of(contractId));
            case EVM_ADDRESS -> cacheLookup(
                    contractId.getEvmAddress(),
                    () -> findByEvmAddress(
                            toBytes(contractId.getEvmAddress()), contractId.getShardNum(), contractId.getRealmNum()));
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
        var id = Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                // Verify shard and realm match when assuming evmAddress is in the 'shard.realm.num' form
                .filter(e -> e.getShard() == shardNum && e.getRealm() == realmNum)
                .or(() -> entityRepository.findByEvmAddress(evmAddress).map(EntityId::of));

        if (id.isEmpty()) {
            Utility.handleRecoverableError("Entity not found for EVM address {}", Hex.encodeHexString(evmAddress));
        }

        return id;
    }

    private Optional<EntityId> findByPublicKeyAlias(byte[] alias, long shardNum, long realmNum) {
        var encodedId = entityRepository.findByAlias(alias);
        if (encodedId.isPresent()) {
            return Optional.ofNullable(EntityId.of(encodedId.get()));
        }

        // Try to fall back to the 20-byte evm address recovered from the ECDSA secp256k1 alias
        var evmAddress = aliasToEvmAddress(alias);
        if (evmAddress == null) {
            Utility.handleRecoverableError("Unable to find entity for alias {}", Hex.encodeHexString(alias));
            return Optional.empty();
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Trying to find entity by evm address {} recovered from public key alias {}",
                    Hex.encodeHexString(evmAddress),
                    Hex.encodeHexString(alias));
        }

        // Check cache first in case the 20-byte evm address hasn't persisted to db
        return cache.get(fromBytes(evmAddress), () -> findByEvmAddress(evmAddress, shardNum, realmNum));
    }
}
