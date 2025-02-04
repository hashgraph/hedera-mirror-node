/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.repository.EntityRepository;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class EntityServiceImpl implements EntityService {

    private final EntityRepository entityRepository;
    private final Validator validator;

    @Override
    public Entity findById(@Nonnull EntityId id) {
        validator.validateShard(id, id.getShard());

        return entityRepository.findById(id.getId())
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + id));
    }

    @Override
    public EntityId lookup(@Nonnull EntityIdParameter accountId) {
        validator.validateShard(accountId, accountId.shard());

        if (accountId.realm() != 0) {
            throw new IllegalArgumentException("ID %s has an invalid realm".formatted(accountId));
        }

        var id = switch (accountId) {
            case EntityIdNumParameter p -> Optional.of(p.id());
            case EntityIdAliasParameter p -> entityRepository.findByAlias(p.alias()).map(EntityId::of);
            case EntityIdEvmAddressParameter p -> entityRepository.findByEvmAddress(p.evmAddress()).map(EntityId::of);
        };

        return id.orElseThrow(() -> new EntityNotFoundException("No account found for the given ID"));
    }
}
