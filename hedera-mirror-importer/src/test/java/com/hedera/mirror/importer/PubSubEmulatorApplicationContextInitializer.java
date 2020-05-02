package com.hedera.mirror.importer;

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

class PubSubEmulatorApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        var env = applicationContext.getEnvironment();
        String enabled = env.getProperty("embedded.google.pubsub.enabled");
        if (enabled != null && enabled.equals("false")) {
            return;
        }
        TestPropertyValues
                .of("spring.cloud.gcp.pubsub.projectId=" + env.getProperty("embedded.google.pubsub.project-id"))
                .and("spring.cloud.gcp.pubsub.emulator-host=" + env.getProperty("embedded.google.pubsub.host") + ":"
                        + env.getProperty("embedded.google.pubsub.port"))
                .applyTo(applicationContext);
    }
}
