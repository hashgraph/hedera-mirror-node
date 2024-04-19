/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaProperties;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.repository.EntityRepository;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {

    private final EntityRepository entityRepository;
    private final RestJavaProperties properties;

    @Override
    @SuppressWarnings("java:S1481")
    public EntityId lookup(EntityIdParameter accountId) {

        if (accountId.shard() != properties.getShard()) {
            throw new IllegalArgumentException("ID %s has invalid shard".formatted(accountId));
        }

        if (accountId.realm() != 0) {
            throw new IllegalArgumentException("ID %s has invalid realm".formatted(accountId));
        }

        Optional<Long> id;
        switch (accountId) {
            case EntityIdNumParameter p -> {
                return p.id();
            }
            case EntityIdAliasParameter p -> id = entityRepository.findByAlias(p.alias());
            case EntityIdEvmAddressParameter p -> id = entityRepository.findByEvmAddress(p.evmAddress());
        }

        if (id.isEmpty()) {
            throw new EntityNotFoundException("No account found for the given ID");
        }
        return EntityId.of(id.get());
    }
}
