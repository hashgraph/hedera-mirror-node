/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureAfter(RedisAutoConfiguration.class)
@Configuration
public class RedisTestConfiguration {
    @Bean
    @ServiceConnection("redis")
    GenericContainer<?> redis() {
        var logger = LoggerFactory.getLogger("RedisContainer");
        return new GenericContainer<>(DockerImageName.parse("redis:7.4"))
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
                .withExposedPorts(6379)
                .withLogConsumer(new Slf4jLogConsumer(logger, true));
    }
}
