/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.config;

import static com.hedera.mirror.common.config.CommonTestConfiguration.POSTGRESQL;

import com.google.common.collect.ImmutableMap;
import com.hedera.mirror.common.config.CommonTestConfiguration.FilteringConsumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class SpecTestConfig {

    public static final String REST_API = "restApi";

    @Value("#{environment.matchesProfiles('v2')}")
    private boolean v2;

    @Bean
    Network postgresqlNetwork() {
        return Network.newNetwork();
    }

    @Bean(POSTGRESQL)
    @ServiceConnection("postgresql")
    PostgreSQLContainer<?> postgresqlOverride(Network postgresqlNetwork) {
        var imageName = v2 ? "gcr.io/mirrornode/citus:12.1.1" : "postgres:16-alpine";
        var dockerImageName = DockerImageName.parse(imageName).asCompatibleSubstituteFor("postgres");
        var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
        var excluded = "terminating connection due to unexpected postmaster exit";
        var logConsumer = new FilteringConsumer(
                new Slf4jLogConsumer(logger, true),
                o -> !StringUtils.contains(o.getUtf8StringWithoutLineEnding(), excluded));
        return new PostgreSQLContainer<>(dockerImageName)
                .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY)
                .withDatabaseName("mirror_node")
                .withLogConsumer(logConsumer)
                .withNetwork(postgresqlNetwork)
                .withNetworkAliases("postgresql")
                .withPassword("mirror_node_pass")
                .withUsername("mirror_node");
    }

    @Bean(REST_API)
    GenericContainer<?> jsRestApi(PostgreSQLContainer<?> postgresql, Network prostgresqlNetwork) {
        var envBuilder = ImmutableMap.<String, String>builder()
                .put("HEDERA_MIRROR_REST_REDIS_ENABLED", "false")
                .put("HEDERA_MIRROR_REST_DB_HOST", "postgresql") // Postgresql container network alias
                .put("HEDERA_MIRROR_REST_DB_PORT", PostgreSQLContainer.POSTGRESQL_PORT.toString());

        if (v2) {
            envBuilder
                    .put("HEDERA_MIRROR_REST_DB_USERNAME", "mirror_rest")
                    .put("HEDERA_MIRROR_REST_DB_PASSWORD", "mirror_rest_pass");
        }

        return new GenericContainer<>(DockerImageName.parse("gcr.io/mirrornode/hedera-mirror-rest:latest"))
                .dependsOn(postgresql)
                .withExposedPorts(5551)
                .withEnv(envBuilder.build())
                .withImagePullPolicy(PullPolicy.alwaysPull())
                .withNetwork(prostgresqlNetwork);
    }
}
