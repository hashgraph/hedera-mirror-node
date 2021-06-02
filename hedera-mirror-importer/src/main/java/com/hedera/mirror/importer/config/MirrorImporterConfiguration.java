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

import java.net.URI;
import java.time.Duration;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.leader.LeaderAspect;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
@AutoConfigureBefore(FlywayAutoConfiguration.class) // Since this configuration creates FlywayConfigurationCustomizer
public class MirrorImporterConfiguration {

    private final MirrorProperties mirrorProperties;
    private final CommonDownloaderProperties downloaderProperties;
    private final MetricsExecutionInterceptor metricsExecutionInterceptor;
    private final AwsCredentialsProvider awsCredentialsProvider;

    @Resource(name = "webServerStartStop")
    private SmartLifecycle webServerStartStop;

    @PostConstruct
    void init() {
        // Start the web server ASAP so kubernetes liveness probe is up before long-running migrations
        webServerStartStop.start();
    }

    @Bean
    @Profile("kubernetes")
    LeaderAspect leaderAspect() {
        return new LeaderAspect();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hedera.mirror.importer.downloader", name = "cloudProvider", havingValue = "GCP")
    public S3AsyncClient gcpCloudStorageClient() {
        log.info("Configured to download from GCP with bucket name '{}'", downloaderProperties.getBucketName());
        // Any valid region for aws client. Ignored by GCP.
        S3AsyncClientBuilder clientBuilder = asyncClientBuilder("us-east-1")
                .endpointOverride(URI.create(downloaderProperties.getCloudProvider().getEndpoint()));
        String projectId = downloaderProperties.getGcpProjectId();
        if (StringUtils.isNotBlank(projectId)) {
            clientBuilder.overrideConfiguration(builder -> builder.addExecutionInterceptor(new ExecutionInterceptor() {
                @Override
                public SdkHttpRequest modifyHttpRequest(
                        Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
                    return context.httpRequest().toBuilder()
                            .appendRawQueryParameter("userProject", projectId).build();
                }
            }));
        }
        return clientBuilder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hedera.mirror.importer.downloader", name = "cloudProvider", havingValue = "S3",
            matchIfMissing = true)
    public S3AsyncClient s3CloudStorageClient() {
        log.info("Configured to download from S3 in region {} with bucket name '{}'",
                downloaderProperties.getRegion(), downloaderProperties.getBucketName());
        S3AsyncClientBuilder clientBuilder = asyncClientBuilder(
                downloaderProperties.getRegion());
        String endpointOverride = downloaderProperties.getEndpointOverride();
        if (endpointOverride != null) {
            log.info("Overriding s3 client endpoint to {}", endpointOverride);
            clientBuilder.endpointOverride(URI.create(endpointOverride));
        }
        return clientBuilder.build();
    }

    private S3AsyncClientBuilder asyncClientBuilder(String region) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(downloaderProperties.getMaxConcurrency())
                .connectionMaxIdleTime(Duration.ofSeconds(5))  // https://github.com/aws/aws-sdk-java-v2/issues/1122
                .build();

        return S3AsyncClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.of(region))
                .httpClient(httpClient)
                .overrideConfiguration(c -> c.addExecutionInterceptor(metricsExecutionInterceptor));
    }

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer(ApplicationContext applicationContext) {
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
