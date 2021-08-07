package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class EntityRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private EntityRepository entityRepository;

    @Test
    void nullCharacter() {
        Entity entity = new Entity();
        entity.setId(1L);
        entity.setNum(1L);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(1);
        entity.setMemo("abc" + (char) 0);
        entity.setDeleted(false);
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void entityPublicKeyUpdates() {
        Entity entity = new Entity();
        entity.setId(1L);
        entity.setNum(1L);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(1);
        entity.setMemo("abc" + (char) 0);
        entity.setDeleted(false);
        entityRepository.save(entity);

        // unset key should result in null public key
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();

        // default proto key of single byte should result in empty public key
        entity.setKey(Key.getDefaultInstance().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isEqualTo("");

        // invalid key should be null
        entity.setKey("123".getBytes());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();

        // valid key should not be null
        entity.setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("123")).build().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNotNull();

        // null key like unset should result in null public key
        entity.setKey(null);
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();
    }

    @Test
    void insertEntityId() {
        // given
        EntityId entityId = EntityId.of(10L, 20L, 30L, EntityTypeEnum.ACCOUNT);
        entityRepository.insertEntityId(entityId);
        assertThat(entityRepository.findById(entityId.getId())).get()
                .isEqualTo(getEntityWithDefaultMemo(entityId));

        // when
        entityRepository.insertEntityId(entityId); // insert again to test for conflict

        assertThat(entityRepository.count()).isEqualTo(1);
        assertThat(entityRepository.findById(entityId.getId())).get().isEqualTo(getEntityWithDefaultMemo(entityId));
    }

    protected Entity getEntityWithDefaultMemo(EntityId entityId) {
        Entity entity = entityId.toEntity();
        entity.setMemo("");
        return entity;
    }
}
