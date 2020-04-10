package com.hedera.datagenerator.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.RandomDistributionFromRange;
import com.hedera.mirror.importer.domain.Entities;

@Log4j2
@Named
@Getter
public class EntityManager {

    private final long nodeAccountId;

    private final EntitySet accounts;

    private final EntitySet files;

    private final EntitySet topics;

    // Keeps track of entities' balances.
    private final Map<Long, Long> balances;

    private final long portalEntity;  // Used to create crypto accounts

    public EntityManager() {
        accounts = new EntitySet(0L);  // Account entities start from 0
        files = new EntitySet(100_000_000L);
        topics = new EntitySet(200_000_000L);
        balances = new HashMap<>();

        // Create one node account with id = 0.
        nodeAccountId = accounts.newEntity().getId();
        balances.put(nodeAccountId, 0L);  // balance of nodeAccountId

        // Create portal account with id = 1 which can fund other accounts on creation.
        portalEntity = accounts.newEntity().getId();
        // Source of all hbars for couple 100 million transactions.
        balances.put(portalEntity, 1000_000_000_000_000_000L);  // balance of nodeAccountId
    }

    public void addBalance(long accountId, long value) {
        long oldBalance = balances.getOrDefault(accountId, 0L);
        balances.put(accountId, oldBalance + value);
    }

    /**
     * Represents set of entities for a specific entity type. Only keeps track of entity ids ever created. Does not keep
     * track of active/deleted entity ids since mirror node does not do rigorous validations like - no update
     * transaction should be done on deleted entity.
     */
    @Getter
    public static class EntitySet {
        private final long startEntityId;
        private final Set<Long> deleted = new HashSet<>();
        private long nextEntityId;
        private Distribution<Long> entitySampler;

        EntitySet(Long startEntityId) {
            this.startEntityId = startEntityId;
            nextEntityId = startEntityId;
            entitySampler = new RandomDistributionFromRange(startEntityId, nextEntityId);
        }

        public Entities getRandom() {
            Entities entity = new Entities();
            entity.setId(entitySampler.sample(1).get(0));
            return entity;
        }

        public List<Long> getRandomIds(int n) {
            return entitySampler.sampleDistinct(n);
        }

        public Entities newEntity() {
            long newEntityId = nextEntityId;
            nextEntityId++;
            entitySampler = new RandomDistributionFromRange(startEntityId, nextEntityId);
            log.trace("New entity {}", newEntityId);
            Entities entities = new Entities();
            entities.setId(newEntityId);
            return entities;
        }

        public void delete(Entities entity) {
            deleted.add(entity.getId());
        }
    }
}
