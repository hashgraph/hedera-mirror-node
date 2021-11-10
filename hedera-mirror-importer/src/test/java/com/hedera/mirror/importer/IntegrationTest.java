package com.hedera.mirror.importer;

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
import java.time.Instant;
import java.util.Collection;
import javax.annotation.Resource;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.util.EntityIdEndec;

import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.postgresql.util.PGobject;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.config.MeterRegistryConfiguration;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;

// Same database is used for all tests, so clean it up before each test.
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/scripts/cleanup.sql")
@SpringBootTest
@Import(MeterRegistryConfiguration.class)
public abstract class IntegrationTest {

    protected final Logger log = LogManager.getLogger(getClass());

    @Resource
    private Collection<CacheManager> cacheManagers;

    @Resource
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    @Resource
    private MirrorProperties mirrorProperties;

    protected static <T> RowMapper<T> rowMapper(Class<T> entityClass) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PGobject.class, Range.class,
                source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(Long.class, EntityId.class,
                id -> EntityIdEndec.decode(id, EntityType.ACCOUNT));

        DataClassRowMapper dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(defaultConversionService);
        return dataClassRowMapper;
    }

    @BeforeEach
    void logTest(TestInfo testInfo) {
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    private void reset() {
        cacheManagers.forEach(c -> c.getCacheNames().forEach(name -> c.getCache(name).clear()));
        mirrorDateRangePropertiesProcessor.clear();
        mirrorProperties.setStartDate(Instant.EPOCH);
    }
}
