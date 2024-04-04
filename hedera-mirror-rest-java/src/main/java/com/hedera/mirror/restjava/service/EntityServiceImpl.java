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

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.restjava.repository.EntityRepository;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {

    private final EntityRepository entityRepository;

    @Override
    public Optional<Entity> lookup(EntityId entityId) {
        return entityRepository.findById(entityId.getId()).filter(e -> e.getType() == EntityType.ACCOUNT);
    }

    @Override
    public Optional<Long> getByAlias(byte[] alias) {
        return entityRepository.findByAlias(alias);
    }

    @Override
    public Optional<Long> getByEvmAddress(byte[] evmAddress) {
        return entityRepository.findByEvmAddress(evmAddress);
    }
}
