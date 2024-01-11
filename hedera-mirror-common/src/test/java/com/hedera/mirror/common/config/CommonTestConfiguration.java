/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.config;

import com.google.common.collect.ImmutableMap;
import com.hedera.mirror.common.domain.DomainBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class CommonTestConfiguration {

    @Value("#{environment.matchesProfiles('v2')}")
    private boolean v2;

    @Bean
    DomainBuilder domainBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        return new DomainBuilder(entityManager, transactionOperations);
    }

    @Bean
    @ConfigurationProperties("spring.flyway")
    @Primary
    FlywayProperties flywayProperties() {
        final var baseLocation = "filesystem:../hedera-mirror-importer/src/main/resources/db/migration/";
        var placeholders = ImmutableMap.<String, String>builder()
                .put("api-password", "mirror_api_pass")
                .put("api-user", "mirror_api")
                .put("db-name", "mirror_node")
                .put("db-user", "mirror_importer")
                .put("idPartitionSize", "1000000000000000")
                .put("maxEntityId", "5000000")
                .put("maxEntityIdRatio", "2.0")
                .put("partitionStartDate", "'1970-01-01'")
                .put("partitionTimeInterval", "'10 years'")
                .put("schema", "public")
                .put("shardCount", "2")
                .put("tempSchema", "temporary")
                .put("topicRunningHashV2AddedTimestamp", "0")
                .build();

        var flywayProperties = new FlywayProperties();

        flywayProperties.setBaselineOnMigrate(true);
        flywayProperties.setBaselineVersion("0");
        flywayProperties.setConnectRetries(10);
        flywayProperties.setIgnoreMigrationPatterns(List.of("*:missing", "*:ignored"));
        flywayProperties.setLocations(List.of(baseLocation + "v1", baseLocation + "common"));
        flywayProperties.setPlaceholders(placeholders);
        flywayProperties.setTarget("latest");

        if (v2) {
            flywayProperties.setBaselineVersion("1.999.999");
            flywayProperties.setLocations(List.of(baseLocation + "v2", baseLocation + "common"));
        }

        return flywayProperties;
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ServiceConnection("postgresql")
    PostgreSQLContainer<?> postgresql() {
        var imageName = v2 ? "gcr.io/mirrornode/citus:12.1.1" : "postgres:14-alpine";
        var dockerImageName = DockerImageName.parse(imageName).asCompatibleSubstituteFor("postgres");
        var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
        return new PostgreSQLContainer<>(dockerImageName)
                .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY)
                .withDatabaseName("mirror_node")
                .withLogConsumer(new Slf4jLogConsumer(logger, true))
                .withPassword("mirror_node_pass")
                .withUsername("mirror_node");
    }
}
