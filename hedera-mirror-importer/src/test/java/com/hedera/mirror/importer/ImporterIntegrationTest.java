/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Range;
import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.common.converter.EntityIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.ImporterIntegrationTest.Configuration;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.converter.JsonbToListConverter;
import com.redis.testcontainers.RedisContainer;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.annotation.Resource;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(SoftAssertionsExtension.class)
@Import(Configuration.class)
public abstract class ImporterIntegrationTest extends CommonIntegrationTest {

    private static final Map<Class<?>, String> DEFAULT_DOMAIN_CLASS_IDS = new ConcurrentHashMap<>();

    @Resource
    protected JdbcOperations jdbcOperations;

    @Resource
    protected RetryRecorder retryRecorder;

    @InjectSoftAssertions
    protected SoftAssertions softly;

    @Resource
    private DateRangeCalculator dateRangeCalculator;

    @Resource
    private ImporterProperties importerProperties;

    @Getter
    @Value("#{environment.matchesProfiles('!v2')}")
    private boolean v1;

    protected static <T> RowMapper<T> rowMapper(Class<T> entityClass) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(
                PGobject.class, Range.class, source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(
                Long.class, EntityId.class, EntityIdConverter.INSTANCE::convertToEntityAttribute);
        defaultConversionService.addConverter(PgArray.class, List.class, array -> {
            try {
                return Arrays.asList((Object[]) array.getArray());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        defaultConversionService.addConverter(new JsonbToListConverter());

        DataClassRowMapper<T> dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(defaultConversionService);
        return dataClassRowMapper;
    }

    protected <T> Collection<T> findEntity(Class<T> entityClass, String ids, String table) {
        String sql = String.format("select * from %s order by %s, timestamp_range asc", table, ids);
        return jdbcOperations.query(sql, rowMapper(entityClass));
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass) {
        var ids = DEFAULT_DOMAIN_CLASS_IDS.computeIfAbsent(historyClass, this::getDefaultIdColumns);
        return findHistory(historyClass, ids);
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass, String ids) {
        return findHistory(historyClass, ids, null);
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass, String ids, String table) {
        if (StringUtils.isEmpty(table)) {
            table = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, historyClass.getSimpleName());
        }
        return findEntity(historyClass, ids, String.format("%s_history", table));
    }

    protected void reset() {
        super.reset();
        dateRangeCalculator.clear();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        importerProperties.setStartDate(Instant.EPOCH);
        retryRecorder.reset();
    }

    protected Boolean tableExists(String name) {
        return jdbcOperations.queryForObject(
                "select exists(select 1 from information_schema.tables where table_name = ?)", Boolean.class, name);
    }

    private String getDefaultIdColumns(Class<?> entityClass) {
        Stream<Field> idFields;
        var idClassAnnotation = AnnotationUtils.findAnnotation(entityClass, IdClass.class);
        if (idClassAnnotation != null) {
            var idClass = idClassAnnotation.value();
            idFields = Arrays.stream(idClass.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()));
        } else {
            idFields = Arrays.stream(FieldUtils.getAllFields(entityClass))
                    .filter(f -> AnnotationUtils.findAnnotation(f, Id.class) != null);
        }

        var idColumns = idFields.map(Field::getName)
                .map(name -> CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(","));

        return !idColumns.isEmpty() ? idColumns : "id";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {

        @Bean
        @ServiceConnection("redis")
        RedisContainer redis() {
            var logger = LoggerFactory.getLogger(RedisContainer.class);
            return new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                    .withLogConsumer(new Slf4jLogConsumer(logger, true));
        }
    }
}
