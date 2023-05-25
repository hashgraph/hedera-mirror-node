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

package com.hedera.mirror.importer.config;

import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderAspect;
import com.hedera.mirror.importer.leader.LeaderService;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.parser.batch.BatchUpserter;
import com.hedera.mirror.importer.repository.upsert.DeletedTokenDissociateTransferUpsertQueryGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EntityScan({"com.hedera.mirror.common.domain", "com.hedera.mirror.importer.repository.upsert"})
@Log4j2
@RequiredArgsConstructor
@AutoConfigureBefore(FlywayAutoConfiguration.class) // Since this configuration creates FlywayConfigurationCustomizer
public class MirrorImporterConfiguration {

    public static final String DELETED_TOKEN_DISSOCIATE_BATCH_PERSISTER =
            "deletedTokenDissociateTransferBatchPersister";

    private final MirrorProperties mirrorProperties;

    @Bean
    @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
    @ConditionalOnProperty(value = "spring.cloud.kubernetes.leader.enabled")
    LeaderAspect leaderAspect() {
        return new LeaderAspect();
    }

    @Bean
    @ConditionalOnMissingBean
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

    @Bean(name = DELETED_TOKEN_DISSOCIATE_BATCH_PERSISTER)
    BatchPersister deletedTokenDissociateTransferBatchPersister(
            DataSource dataSource, MeterRegistry meterRegistry, CommonParserProperties parserProperties) {
        return new BatchUpserter(
                TokenTransfer.class,
                dataSource,
                meterRegistry,
                parserProperties,
                new DeletedTokenDissociateTransferUpsertQueryGenerator());
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableRetry
    protected static class RetryConfiguration {}

    @Configuration
    @ConditionalOnProperty(
            prefix = "spring.task.scheduling",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {}
}
