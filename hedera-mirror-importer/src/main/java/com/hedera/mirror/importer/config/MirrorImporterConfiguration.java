package com.hedera.mirror.importer.config;

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

import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;

import com.hedera.mirror.importer.downloader.StreamProperties;
import com.hedera.mirror.importer.leader.LeaderAspect;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
public class MirrorImporterConfiguration {

    private final StreamProperties streamProperties;

    @Bean
    @Profile("kubernetes")
    LeaderAspect leaderAspect() {
        return new LeaderAspect();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hedera.mirror.downloader.stream.gcs", name = "enabled", havingValue = "true")
    public S3AsyncClient gcpCloudStorageClient() {
        StreamProperties.GCSProperties gcsProperties = streamProperties.getGcs();
        log.info("Configured to download from GCP with bucket name '{}'", streamProperties.getBucketName());
        S3AsyncClientBuilder clientBuilder = asyncClientBuilder(gcsProperties)
                .region(Region.US_EAST_1); // Any valid value for aws client. Ignored by GCP server.
        if (gcsProperties.getProjectId() != null) {
            ExecutionInterceptor executionInterceptor = new ExecutionInterceptor() {
                @Override
                public SdkHttpRequest modifyHttpRequest(
                        Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
                    return context.httpRequest().toBuilder()
                            .appendRawQueryParameter("userProject", gcsProperties.getProjectId()).build();
                }
            };
            clientBuilder.overrideConfiguration(builder -> builder.addExecutionInterceptor(executionInterceptor));
        }
        return clientBuilder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hedera.mirror.downloader.stream.s3", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public S3AsyncClient s3CloudStorageClient() {
        StreamProperties.S3Properties s3Properties = streamProperties.getS3();
        log.info("Configured to download from S3 in region {} with bucket name '{}'",
                s3Properties.getRegion(), streamProperties.getBucketName());
        return asyncClientBuilder(s3Properties)
                .region(Region.of(s3Properties.getRegion()))
                .build();
    }

    private S3AsyncClientBuilder asyncClientBuilder(StreamProperties.CommonProperties properties) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(streamProperties.getMaxConcurrency())
                .connectionMaxIdleTime(Duration.ofSeconds(5))  // https://github.com/aws/aws-sdk-java-v2/issues/1122
                .build();

        S3AsyncClientBuilder clientBuilder = S3AsyncClient.builder()
                .credentialsProvider(awsCredentialsProvider(properties.getAccessKey(), properties.getSecretKey()))
                .httpClient(httpClient);
        if (properties.getEndpoint() != null) { // endpoint set explicitly for local testing
            clientBuilder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return clientBuilder;
    }

    private AwsCredentialsProvider awsCredentialsProvider(String accessKey, String secretKey) {
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            log.info("Setting up S3 async client using provided access/secret key");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        } else {
            log.info("Setting up S3 async client using anonymous credentials");
            return AnonymousCredentialsProvider.create();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.task.scheduling", name = "enabled", havingValue = "true", matchIfMissing
            = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {
    }
}
