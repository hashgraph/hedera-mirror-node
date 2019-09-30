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

import com.hedera.mirror.domain.Entities;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityRepositoryTest extends AbstractRepositoryTest {

    @Test
    void findByIdAndPrimaryKey() {
    	
    	final long entityShard = 3;
    	final long entityRealm = 2;
    	final long entityNum = 1;
    	final long entityId = 1;
    	final long autoRenewPeriod = 100;
    	final boolean deleted = false;
    	final String publicKeyHex = "ed25519publickeyhex";
    	final long expiryTimeNanos = 200;
    	final long expiryTimeSeconds = 300;
    	final long expiryTimeNs = 400;
    	final byte[] key = "key".getBytes();
    	
    	final int entityTypeId = entityTypeRepository.findByName("account").get().getId();
    			
    	Entities entity = new Entities();
    	entity.setId(entityId);
    	entity.setAutoRenewPeriod(autoRenewPeriod);
    	entity.setDeleted(deleted);
    	entity.setEd25519PublicKeyHex(publicKeyHex);
    	entity.setEntityNum(entityNum);
    	entity.setEntityRealm(entityRealm);
    	entity.setEntityShard(entityShard);
    	entity.setEntityTypeId(entityTypeId);
    	entity.setExpiryTimeNanos(expiryTimeNanos);
    	entity.setExpiryTimeNs(expiryTimeNs);
    	entity.setExpiryTimeSeconds(expiryTimeSeconds);
    	entity.setKey(key);
    	entity.setProxyAccountId(entityId);
    	entity = entityRepository.save(entity);
    	
    	final Entities newEntity = entityRepository.findById(entity.getId()).get();
    	
    	assertAll(
                () -> assertEquals(entityShard, newEntity.getEntityShard())
                ,() -> assertEquals(entityRealm, newEntity.getEntityRealm())
                ,() -> assertEquals(entityNum, newEntity.getEntityNum())
                ,() -> assertEquals(entityTypeId, newEntity.getEntityTypeId())
        );
    	
    	final Entities entityByPrimaryKey = entityRepository.findByPrimaryKey(entityShard, entityRealm, entityNum).get();
    	
    	assertAll(
            () -> assertEquals(entityShard, entityByPrimaryKey.getEntityShard())
            ,() -> assertEquals(entityRealm, entityByPrimaryKey.getEntityRealm())
            ,() -> assertEquals(entityNum, entityByPrimaryKey.getEntityNum())
            ,() -> assertEquals(autoRenewPeriod, entityByPrimaryKey.getAutoRenewPeriod())
            ,() -> assertEquals(deleted, entityByPrimaryKey.isDeleted())
            ,() -> assertEquals(publicKeyHex, entityByPrimaryKey.getEd25519PublicKeyHex())
            ,() -> assertEquals(entityTypeId, entityByPrimaryKey.getEntityTypeId())
            ,() -> assertEquals(expiryTimeNanos, entityByPrimaryKey.getExpiryTimeNanos())
            ,() -> assertEquals(expiryTimeSeconds, entityByPrimaryKey.getExpiryTimeSeconds())
            ,() -> assertEquals(expiryTimeNs, entityByPrimaryKey.getExpiryTimeNs())
            ,() -> assertArrayEquals(key, entityByPrimaryKey.getKey())
            ,() -> assertEquals(entityId, entityByPrimaryKey.getProxyAccountId())
		);
    }
}
