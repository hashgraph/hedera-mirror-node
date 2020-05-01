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

import javax.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;

@Log4j2
class PubSubEmulatorApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static GenericContainer emulator;

    // Try starting pubsub emulator. Will fail if docker daemon is absent on the machine running the tests,
    // basically when running in CircleCI. In such cases, spring.cloud.gcp.pubsub.emulator-host is expected
    // to be correctly set and pointing to a running pubsub emulator.
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String projectId = applicationContext.getEnvironment().getProperty("spring.cloud.gcp.pubsub.projectId");
        try {
            log.info("Starting PubSub emulator");
            var emulator = startPubSubEmulator(projectId);
            String pubsubEmulatorHost = emulator.getContainerIpAddress() + ":" + emulator.getMappedPort(8681);
            log.info("Pubsub emulator running at {}", pubsubEmulatorHost);
            TestPropertyValues.of("spring.cloud.gcp.pubsub.emulator-host=" + pubsubEmulatorHost)
                    .applyTo(applicationContext);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (emulator != null && emulator.isRunning()) {
            log.info("Stopping PubSub emulator");
            emulator.stop();
        }
    }

    private static GenericContainer startPubSubEmulator(String projectId) {
        emulator = new GenericContainer("messagebird/gcloud-pubsub-emulator:latest")
                .withExposedPorts(8681)
                .withExposedPorts(8682)
                .waitingFor(Wait.forListeningPort())
                .withEnv("PUBSUB_PROJECT1", projectId)
                .withLogConsumer(frame ->
                        log.debug("{} : {}", ((OutputFrame) frame).getType(), ((OutputFrame) frame).getUtf8String()));
        emulator.start();
        return emulator;
    }
}
