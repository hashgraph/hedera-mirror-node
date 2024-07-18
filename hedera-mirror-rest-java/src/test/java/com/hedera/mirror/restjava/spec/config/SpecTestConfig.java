/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.config.CommonTestConfiguration.FilteringConsumer;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class SpecTestConfig {

    @Bean
    Network postgresqlNetwork() {
        return Network.newNetwork();
    }

    @Bean("postgresql")
    @ServiceConnection("postgresql")
    PostgreSQLContainer<?> postgresqlOverride(Network postgresqlNetwork) {
        var dockerImageName = DockerImageName.parse("postgres:14-alpine").asCompatibleSubstituteFor("postgres");
        var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
        var excluded = "terminating connection due to unexpected postmaster exit";
        var logConsumer = new FilteringConsumer(
                new Slf4jLogConsumer(logger, true),
                o -> !StringUtils.contains(o.getUtf8StringWithoutLineEnding(), excluded));
        return new PostgreSQLContainer<>(dockerImageName)
                .withNetwork(postgresqlNetwork)
                .withNetworkAliases("postgresql")
                .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY)
                .withDatabaseName("mirror_node")
                .withLogConsumer(logConsumer)
                .withPassword("mirror_node_pass")
                .withUsername("mirror_node");
    }

    @Bean
    GenericContainer<?> jsRestApi(PostgreSQLContainer<?> postgresql, Network prostgresqlNetwork) {
        return new GenericContainer<>(
                DockerImageName.parse("gcr.io/mirrornode/hedera-mirror-rest:latest"))
//                    new ImageFromDockerfile("localhost/testcontainers/restapi", false)
//                            .withDockerfile(Path.of("../hedera-mirror-rest/Dockerfile")))
                .dependsOn(postgresql)
                .withNetwork(prostgresqlNetwork)
                .withExposedPorts(5551)
                .withEnv(Map.of(
                        "HEDERA_MIRROR_REST_REDIS_ENABLED", "false",
                        "HEDERA_MIRROR_REST_DB_HOST", "postgresql", // Postgresql container network alias
                        "HEDERA_MIRROR_REST_DB_PORT", PostgreSQLContainer.POSTGRESQL_PORT.toString()));
    }
}
