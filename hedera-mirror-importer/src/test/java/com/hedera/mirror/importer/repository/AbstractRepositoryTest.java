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

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.annotation.Resource;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public abstract class AbstractRepositoryTest extends IntegrationTest {

    @Resource
    protected DomainBuilder domainBuilder;

    @Resource
    protected JdbcOperations jdbcOperations;

    protected static <T> RowMapper<T> rowMapper(Class<T> entityClass) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PGobject.class, Range.class,
                source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(Long.class, EntityId.class,
                id -> EntityId.of(0L, 0L, id, EntityTypeEnum.ACCOUNT));

        DataClassRowMapper dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(defaultConversionService);
        return dataClassRowMapper;
    }
}
