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

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class EntityRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<Entity> ROW_MAPPER;

    static {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PGobject.class, Range.class,
                source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(Long.class, EntityId.class,
                id -> EntityId.of(0L, 0L, id, EntityTypeEnum.ACCOUNT));
        DataClassRowMapper dataClassRowMapper = new DataClassRowMapper<>(Entity.class);
        dataClassRowMapper.setConversionService(defaultConversionService);
        ROW_MAPPER = dataClassRowMapper;
    }

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
    void entityPublicKeyUpdates() {
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

    @Test
    void entityHistory() {
        Entity entityInitial = domainBuilder.entity().persist();

        assertThat(entityRepository.findAll()).containsExactly(entityInitial);
        assertThat(jdbcOperations.queryForObject("select count(*) from entity_history", Integer.class)).isZero();

        var range = Range.atLeast(entityInitial.getModifiedTimestamp() + 1L);
        Entity entityUpdated = entityInitial.toBuilder().memo("Updated").timestampRange(range).build();
        entityRepository.save(entityUpdated);

        assertThat(entityRepository.findAll()).containsExactly(entityUpdated);
        List<Entity> entityHistory = jdbcOperations.query("select * from entity_history", ROW_MAPPER);
        assertThat(entityHistory)
                .hasSize(1)
                .first()
                .returns(Range.closedOpen(entityInitial.getModifiedTimestamp(), entityUpdated.getModifiedTimestamp()),
                        Entity::getTimestampRange)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Range.class)
                .isEqualTo(entityInitial);
    }
}
