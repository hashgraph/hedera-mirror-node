package com.hedera.mirror.grpc;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Sets up application properties based on testcontainers if any enabled.
 */
public class DatabaseApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        var env = applicationContext.getEnvironment();
        String enabled = env.getProperty("embedded.postgresql.enabled");
        if (enabled != null && enabled.equals("false")) {
            return;
        }
        TestPropertyValues
                .of("hedera.mirror.grpc.db.host=" + env.getProperty("embedded.postgresql.host"))
                .and("hedera.mirror.grpc.db.port=" + env.getProperty("embedded.postgresql.port"))
                .and("hedera.mirror.grpc.db.name=" + env.getProperty("embedded.postgresql.schema"))
                .and("hedera.mirror.grpc.db.password=" + env.getProperty("embedded.postgresql.password"))
                .and("hedera.mirror.grpc.db.username=" + env.getProperty("embedded.postgresql.user"))
                .applyTo(applicationContext);
    }
}
