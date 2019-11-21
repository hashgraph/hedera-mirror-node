package com.hedera.faker.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import java.util.Random;
import java.util.Set;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.faker.sampling.Distribution;
import com.hedera.faker.sampling.RandomDistributionFromRange;

@Log4j2
@Named
public class EntityManager {

    @Getter
    private final long nodeAccountId;

    @Getter
    private final EntitySet accounts;

    @Getter
    private final EntitySet files;

    // Keeps track of entities' balances.
    @Getter
    private final Map<Long, Long> balances;

    @Getter
    private final long portalEntity;  // Used to create crypto accounts

    public EntityManager() {
        accounts = new EntitySet(0L);  // Account entities start from 0
        files = new EntitySet(100_000_000L);
        balances = new HashMap<>();

        // Create one node account with id = 0.
        nodeAccountId = accounts.newEntity();
        balances.put(nodeAccountId, 0L);  // balance of nodeAccountId

        // Create portal account with id = 1 which can fund other accounts on creation.
        portalEntity = accounts.newEntity();
        // Source of all hbars for couple 100 million transactions.
        balances.put(portalEntity, 1000_000_000_000_000_000L);  // balance of nodeAccountId
    }

    public void addBalance(long accountId, long value) {
        balances.put(accountId, balances.get(accountId) + value);
    }

    /**
     * Represents set of entities for a specific entity type.
     * Only keeps track of entity ids ever created. Does not keep track of active/deleted entity ids
     * since mirror node does not do rigorous validations like - no update transaction should be done on deleted entity.
     */
    public class EntitySet {
        @Getter
        Long startEntityId;

        @Getter
        Long nextEntityId;

        Distribution<Long> entitySampler;

        @Getter
        final Set<Long> deleted = new HashSet<>();

        EntitySet(Long startEntityId) {
            this.startEntityId = startEntityId;
            nextEntityId = startEntityId;
            entitySampler = new RandomDistributionFromRange(startEntityId, nextEntityId);
        }

        public Long getRandom() {
            return entitySampler.sample(1).get(0);
        }

        public List<Long> getRandom(int n) {
            return entitySampler.sampleDistinct(n);
        }

        public Long newEntity() {
            long newEntityId = nextEntityId;
            nextEntityId++;
            entitySampler = new RandomDistributionFromRange(startEntityId, nextEntityId);
            balances.put(newEntityId, 0L);  // initial balance
            log.trace("New entity {}", newEntityId);
            return newEntityId;
        }

        public void delete(Long entityId) {
            deleted.add(entityId);
        }
    }
}
