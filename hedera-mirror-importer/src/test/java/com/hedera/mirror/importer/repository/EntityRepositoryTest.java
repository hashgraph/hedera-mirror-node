/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class EntityRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    private final EntityRepository entityRepository;

    @Test
    void nullCharacter() {
        Entity entity =
                domainBuilder.entity().customize(e -> e.memo("abc" + (char) 0)).persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void publicKeyUpdates() {
        Entity entity = domainBuilder.entity().customize(b -> b.key(null)).persist();

        // unset key should result in null public key
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNull();

        // default proto key of single byte should result in empty public key
        entity.setKey(Key.getDefaultInstance().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isEqualTo("");

        // invalid key should be null
        entity.setKey("123".getBytes());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNull();

        // valid key should not be null
        entity.setKey(Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("123"))
                .build()
                .toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNotNull();

        // null key like unset should result in null public key
        entity.setKey(null);
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entity::getPublicKey)
                .isNull();
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
        Entity entity = domainBuilder.entity().persist();
        byte[] alias = entity.getAlias();

        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
    }

    @Test
    void findByEvmAddress() {
        Entity entity = domainBuilder.entity().persist();
        Entity entityDeleted =
                domainBuilder.entity().customize(b -> b.deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity.getId());
        assertThat(entityRepository.findByEvmAddress(entityDeleted.getEvmAddress()))
                .isEmpty();
        assertThat(entityRepository.findByEvmAddress(new byte[] {1, 2, 3})).isEmpty();
    }

    @Test
    void updateContractType() {
        Entity entity = domainBuilder.entity().persist();
        Entity entity2 = domainBuilder.entity().persist();
        entityRepository.updateContractType(List.of(entity.getId(), entity2.getId()));
        assertThat(entityRepository.findAll())
                .hasSize(2)
                .extracting(Entity::getType)
                .allMatch(e -> e == CONTRACT);
    }
}
