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
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.importer.domain.DomainBuilder;

class EntityRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private JdbcOperations jdbcOperations;

    @Test
    void nullCharacter() {
        Entity entity = domainBuilder.entity().customize(e -> e.memo("abc" + (char) 0)).persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void publicKeyUpdates() {
        Entity entity = domainBuilder.entity().customize(b -> b.key(null)).persist();

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

    /**
     * This test verifies that the Entity domain object and table definition are in sync with the entity_history table.
     */
    @Test
    void history() {
        Entity entity = domainBuilder.entity().persist();

        jdbcOperations.update("insert into entity_history select * from entity");
        List<Entity> entityHistory = jdbcOperations.query("select * from entity_history", ROW_MAPPER);

        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(entityHistory).containsExactly(entity);
    }

    @Test
    void findByAlias() {
        Entity entity = domainBuilder.entity().get();
        byte[] alias = entity.getAlias();

        // Cache and DB return nothing on empty table
        assertThat(entityRepository.findByAlias(alias)).isNotPresent();

        // DB state is reflected in cache
        entityRepository.save(entity);
        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());

        // Cache returns ID after DB is cleared
        entityRepository.deleteAll();
        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());

        // A new byte[] with the same data retrieves the same value from the cache
        byte[] aliasCopy = Arrays.copyOf(alias, alias.length);
        assertThat(entityRepository.findByAlias(aliasCopy)).get().isEqualTo(entity.getId());
    }

    @Test
    void storeAlias() {
        Entity entity = domainBuilder.entity().get();
        byte[] alias = entity.getAlias();
        entityRepository.storeAlias(alias, entity.getId());
        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
    }
}
