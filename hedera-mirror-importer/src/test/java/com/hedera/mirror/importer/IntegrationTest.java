/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.importer.config.IntegrationTestConfiguration;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.annotation.Resource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(SoftAssertionsExtension.class)
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
public abstract class IntegrationTest {

    private static final RowMapper<NonFeeTransfer> NON_FEE_TRANSFER_ROW_MAPPER = rowMapper(NonFeeTransfer.class);
    private static final String SELECT_NON_FEE_TRANSFERS_QUERY = "select * from non_fee_transfer";

    protected final Logger log = LogManager.getLogger(getClass());

    @Resource
    protected DomainBuilder domainBuilder;

    @Resource
    protected JdbcOperations jdbcOperations;

    @Resource
    protected IntegrationTestConfiguration.RetryRecorder retryRecorder;

    @InjectSoftAssertions
    protected SoftAssertions softly;

    @Resource
    private Collection<CacheManager> cacheManagers;

    @Resource
    private String cleanupSql;

    @Resource
    private MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    @Resource
    private MirrorProperties mirrorProperties;

    @Getter
    @Value("#{environment.acceptsProfiles('!v2')}")
    private boolean v1;

    protected static <T> RowMapper<T> rowMapper(Class<T> entityClass) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(
                PGobject.class, Range.class, source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(
                Long.class, EntityId.class, AccountIdConverter.INSTANCE::convertToEntityAttribute);
        defaultConversionService.addConverter(PgArray.class, List.class, array -> {
            try {
                return Arrays.asList((Object[]) array.getArray());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        DataClassRowMapper<T> dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(defaultConversionService);
        return dataClassRowMapper;
    }

    @BeforeEach
    void logTest(TestInfo testInfo) {
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    protected <T> Collection<T> findEntity(Class<T> entityClass, String ids, String table) {
        String sql = String.format("select * from %s order by %s, timestamp_range asc", table, ids);
        return jdbcOperations.query(sql, rowMapper(entityClass));
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass) {
        return findHistory(historyClass, "id");
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

    protected List<NonFeeTransfer> findNonFeeTransfers() {
        return jdbcOperations.query(SELECT_NON_FEE_TRANSFERS_QUERY, NON_FEE_TRANSFER_ROW_MAPPER);
    }

    protected void reset() {
        cacheManagers.forEach(
                c -> c.getCacheNames().forEach(name -> c.getCache(name).clear()));
        mirrorDateRangePropertiesProcessor.clear();
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
        mirrorProperties.setStartDate(Instant.EPOCH);
        jdbcOperations.execute(cleanupSql);
        retryRecorder.reset();
    }
}
