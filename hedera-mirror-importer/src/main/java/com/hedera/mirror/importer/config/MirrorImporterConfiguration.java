package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderAspect;
import com.hedera.mirror.importer.leader.LeaderService;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
@AutoConfigureBefore(FlywayAutoConfiguration.class) // Since this configuration creates FlywayConfigurationCustomizer
public class MirrorImporterConfiguration {

    private final MirrorProperties mirrorProperties;

    @Autowired(required = false)
    @Qualifier("webServerStartStop")
    private SmartLifecycle webServerStartStop;

    @PostConstruct
    void init() {
        // Start the web server ASAP so kubernetes liveness probe is up before long-running migrations
        if (webServerStartStop != null) {
            webServerStartStop.start();
        }
    }

    @Bean
    @Profile("kubernetes")
    LeaderService leaderAspect() {
        return new LeaderAspect();
    }

    @Bean
    @Profile("!kubernetes")
    LeaderService leaderService() {
        return Boolean.TRUE::booleanValue; // Leader election not available outside Kubernetes
    }

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            Long timestamp = mirrorProperties.getTopicRunningHashV2AddedTimestamp();
            if (timestamp == null) {
                if (mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.MAINNET) {
                    timestamp = 1592499600000000000L;
                } else {
                    timestamp = 1588706343553042000L;
                }
            }
            configuration.getPlaceholders().put("topicRunningHashV2AddedTimestamp", timestamp.toString());
        };
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableRetry
    protected static class RetryConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.task.scheduling", name = "enabled", havingValue = "true", matchIfMissing
            = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {
    }
}
