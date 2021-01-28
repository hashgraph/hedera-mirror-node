package com.hedera.datagenerator.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.RandomDistributionFromRange;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

@Log4j2
@Named
@Getter
public class EntityManager {

    private final EntityId nodeAccount;

    private final EntitySet accounts;

    private final EntitySet files;

    private final EntitySet topics;

    // Keeps track of entities' balances.
    private final Map<EntityId, Long> balances;

    private EntityId portalEntity;  // Used to create crypto accounts

    public EntityManager() {
        accounts = new EntitySet(1L);  // Account entities start from 1
        files = new EntitySet(100_000_000L);
        topics = new EntitySet(200_000_000L);
        balances = new HashMap<>();

        // Create one node account with entity num = 0.
        nodeAccount = accounts.newEntity();
        balances.put(nodeAccount, 0L);  // balance of node account

        // Create portal account with entity num = 1 which can fund other accounts on creation.
        portalEntity = accounts.newEntity();
        // Source of all hbars for couple 100 million transactions.
        balances.put(portalEntity, 1000_000_000_000_000_000L);  // balance of node account
    }

    public void addBalance(EntityId account, long value) {
        long oldBalance = balances.getOrDefault(account, 0L);
        balances.put(account, oldBalance + value);
    }

    /**
     * Represents set of entities for a specific entity type. Only keeps track of entity nums ever created. Does not keep
     * track of active/deleted entities since mirror node does not do rigorous validations like - no update
     * transaction should be done on deleted entity.
     */
    @Getter
    public static class EntitySet {
        private final long startEntityNum;
        private final Set<Long> deleted = new HashSet<>();
        private long entityNum;
        private Distribution<Long> entitySampler;

        EntitySet(Long startEntityNum) {
            this.startEntityNum = startEntityNum;
            entityNum = startEntityNum;
            entitySampler = new RandomDistributionFromRange(startEntityNum, entityNum);
        }

        public EntityId getRandomEntity() {
            return EntityId.of(0L, 0L, entitySampler.sample(1).get(0), EntityTypeEnum.ACCOUNT);
        }

        public List<EntityId> getRandomEntityNum(int n) {
            return entitySampler.sampleDistinct(n).stream()
                    .map(entityNum -> EntityId.of(0L, 0L, entityNum, EntityTypeEnum.ACCOUNT))
                    .collect(Collectors.toList());
        }

        public EntityId newEntity() {
            long entityNum = this.entityNum;
            this.entityNum++;
            entitySampler = new RandomDistributionFromRange(startEntityNum, this.entityNum);
            log.trace("New entity {}", entityNum);
            return EntityId.of(0L, 0L, entityNum, EntityTypeEnum.ACCOUNT);
        }

        public void delete(EntityId entity) {
            deleted.add(entity.getEntityNum());
        }
    }
}
