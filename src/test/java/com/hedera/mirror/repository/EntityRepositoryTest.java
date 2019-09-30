package com.hedera.mirror.repository;

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

import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.ContractResult;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.EntityType;
import com.hedera.mirror.domain.RecordFile;
import com.hedera.mirror.domain.Transaction;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.domain.TransactionType;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

public class EntityRepositoryTest extends IntegrationTest {

    @Resource
    private EntityRepository entityRepository;
    @Resource
    private EntityTypeRepository entityTypeRepository;
    @Resource

    @Test
    void updateStatusValue() {
    	
    	final long shard = 3;
    	final long realm = 2;
    	final long num = 1;
    	final int entityTypeId = entityTypeRepository.findByName("account").get().getId();
    			
    	Entities entity = new Entities();
    	entity.setId(1L);
    	entity.setAutoRenewPeriod(100L);
    	entity.setDeleted(false);
    	entity.setEd25519PublicKeyHex("ed25519PublicKeyHex");
    	entity.setEntityNum(num);
    	entity.setEntityRealm(realm);
    	entity.setEntityShard(shard);
    	entity.setEntityTypeId(entityTypeId);
    	entity.setExpiryTimeNanos(200L);
    	entity.setExpiryTimeNs(300L);
    	entity.setExpiryTimeSeconds(400L);
    	entity.setKey("key".getBytes());
    	entity.setProxyAccountId(null);
    	entity = entityRepository.save(entity);
    	
    	final Entities newEntity = entityRepository.findById(entity.getId()).get();
    	
    	assertAll(
                () -> assertEquals(shard, newEntity.getEntityShard())
                ,() -> assertEquals(realm, newEntity.getEntityRealm())
                ,() -> assertEquals(num, newEntity.getEntityNum())
                ,() -> assertEquals(entityTypeId, newEntity.getEntityTypeId())
        );
    	
    	final Entities entityByPrimaryKey = entityRepository.findByPrimaryKey(shard, realm, num).get();
    	
    	assertAll(
                () -> assertEquals(shard, entityByPrimaryKey.getEntityShard())
                ,() -> assertEquals(realm, entityByPrimaryKey.getEntityRealm())
                ,() -> assertEquals(num, entityByPrimaryKey.getEntityNum())
        );
    	

    }
}
