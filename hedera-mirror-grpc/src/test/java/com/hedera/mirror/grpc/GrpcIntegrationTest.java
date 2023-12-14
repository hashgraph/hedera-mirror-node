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

package com.hedera.mirror.grpc;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.grpc.GrpcIntegrationTest.Configuration;
import com.redis.testcontainers.RedisContainer;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@Import(Configuration.class)
public abstract class GrpcIntegrationTest extends CommonIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @ServiceConnection("redis")
        RedisContainer redis() {
            var logger = LoggerFactory.getLogger(RedisContainer.class);
            return new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                    .withLogConsumer(new Slf4jLogConsumer(logger, true));
        }

        @Bean
        @Primary
        TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
