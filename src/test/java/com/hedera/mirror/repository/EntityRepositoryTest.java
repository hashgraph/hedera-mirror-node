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
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;


@Sql(executionPhase= Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts="classpath:db/scripts/cleanup.sql") // Class manually commits so have to manually cleanup tables
public class EntityRepositoryTest extends AbstractRepositoryTest {

    @Test
    void findByPrimaryKey() {
    	final int entityTypeId = entityTypeRepository.findByName("account").get().getId();

    	Entities proxyEntity = new Entities();
    	proxyEntity.setEntityTypeId(entityTypeId);
    	proxyEntity.setEntityShard(0L);
    	proxyEntity.setEntityRealm(0L);
    	proxyEntity.setEntityNum(100L);
    	proxyEntity = entityRepository.save(proxyEntity);

		Entities entity = new Entities();
    	entity.setAutoRenewPeriod(100L);
    	entity.setDeleted(true);
    	entity.setEd25519PublicKeyHex("ed25519publickeyhex");
        entity.setEntityNum(5L);
        entity.setEntityRealm(4L);
        entity.setEntityShard(3L);
    	entity.setEntityTypeId(entityTypeId);
    	entity.setExpiryTimeNanos(200L);
    	entity.setExpiryTimeNs(300L);
    	entity.setExpiryTimeSeconds(400L);
    	entity.setKey("key".getBytes());
    	entity.setProxyAccountId(proxyEntity.getId());
    	entity = entityRepository.save(entity);

    	assertThat(entityRepository.findByPrimaryKey(entity.getEntityShard(), entity.getEntityRealm(), entity.getEntityNum()).get())
			.isNotNull()
			.isEqualTo(entity);
    }
}
