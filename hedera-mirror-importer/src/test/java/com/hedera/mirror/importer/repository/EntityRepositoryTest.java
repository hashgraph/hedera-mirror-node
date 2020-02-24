package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.Entities;

public class EntityRepositoryTest extends AbstractRepositoryTest {

    @Test
    void findByPrimaryKey() {
        int entityTypeId = entityTypeRepository.findByName("account").get().getId();

        Entities autoRenewAccount = new Entities();
        autoRenewAccount.setEntityTypeId(entityTypeId);
        autoRenewAccount.setEntityShard(0L);
        autoRenewAccount.setEntityRealm(0L);
        autoRenewAccount.setEntityNum(101L);
        autoRenewAccount = entityRepository.save(autoRenewAccount);

        Entities proxyEntity = new Entities();
        proxyEntity.setEntityTypeId(entityTypeId);
        proxyEntity.setEntityShard(0L);
        proxyEntity.setEntityRealm(0L);
        proxyEntity.setEntityNum(100L);
        proxyEntity = entityRepository.save(proxyEntity);

        Entities entity = new Entities();
        entity.setAutoRenewPeriod(100L);
        entity.setAutoRenewAccount(autoRenewAccount);
        entity.setDeleted(true);
        entity.setEd25519PublicKeyHex("0123456789abcdef");
        entity.setEntityNum(5L);
        entity.setEntityRealm(4L);
        entity.setEntityShard(3L);
        entity.setEntityTypeId(entityTypeId);
        entity.setExpiryTimeNs(300L);
        entity.setKey("key".getBytes());
        entity.setProxyAccountId(proxyEntity.getId());
        entity.setSubmitKey("SubmitKey".getBytes());
        entity = entityRepository.save(entity);

        assertThat(entityRepository
                .findByPrimaryKey(entity.getEntityShard(), entity.getEntityRealm(), entity.getEntityNum()))
                .get()
                .isEqualTo(entity);

        entity.setExpiryTimeNs(600L);
        entity = entityRepository.save(entity);

        assertThat(entityRepository
                .findByPrimaryKey(entity.getEntityShard(), entity.getEntityRealm(), entity.getEntityNum()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findEntityIdByNativeIds() {
        var entityTypeId = entityTypeRepository.findByName("account").get().getId();

        var entity = new Entities();
        entity.setEntityTypeId(entityTypeId);
        entity.setEntityShard(1L);
        entity.setEntityRealm(2L);
        entity.setEntityNum(3L);
        final var expected = entityRepository.save(entity);

        var entityId = entityRepository.findEntityIdByNativeIds(entity.getEntityShard(), entity.getEntityRealm(),
                entity.getEntityNum()).get();

        assertAll(() -> assertEquals(expected.getId(), entityId.getId())
                ,() -> assertEquals(expected.getEntityShard(), entityId.getEntityShard())
                ,() -> assertEquals(expected.getEntityRealm(), entityId.getEntityRealm())
                ,() -> assertEquals(expected.getEntityNum(), entityId.getEntityNum())
        );
    }
}
