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

import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdType;
import com.hedera.mirror.restjava.repository.EntityRepository;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@Named
@RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {

    @Value("${hedera.mirror.restJava.shard}")
    private final long systemShard;

    private final EntityRepository entityRepository;

    @Override
    public long lookup(EntityIdParameter accountId) {

        long id = 0;

        if ((accountId.shard() != null && accountId.shard() != systemShard) || accountId.realm() != 0) {
            throw new IllegalArgumentException("Id %s has invalid shard or realm".formatted(accountId));
        }

        if (accountId.type() == EntityIdType.NUM && accountId.num() != null) {
            if (!entityRepository.existsById(accountId.num().getId())) {
                throw new EntityNotFoundException("Id %s does not exist".formatted(accountId));
            }
            id = accountId.num().getId();
        } else if (accountId.type() == EntityIdType.ALIAS && accountId.alias() != null) {
            var entityId = entityRepository.findByAlias(accountId.alias());
            if (!entityId.isPresent()) {
                throw new EntityNotFoundException("No account with a matching evm address found");
            }
            id = entityId.get();
        } else if (accountId.type() == EntityIdType.EVMADDRESS && accountId.evmAddress() != null) {
            var entityId = entityRepository.findByEvmAddress(accountId.evmAddress());
            if (!entityId.isPresent()) {
                throw new EntityNotFoundException("No account with a matching alias found");
            }
            id = entityId.get();
        }
        return id;
    }
}
