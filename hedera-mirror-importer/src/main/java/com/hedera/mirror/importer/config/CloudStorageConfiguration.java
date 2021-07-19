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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class CloudStorageConfiguration {

    private final CommonDownloaderProperties downloaderProperties;
    private final MetricsExecutionInterceptor metricsExecutionInterceptor;

    @Bean
    AwsCredentialsProvider awsCredentialsProvider() {
        if (downloaderProperties.isAnonymousCredentials()) {
            log.info("Setting up S3 async client using anonymous credentials");
            return AnonymousCredentialsProvider.create();
        } else if (downloaderProperties.isStaticCredentials()) {
            log.info("Setting up S3 async client using provided access/secret key");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(downloaderProperties.getAccessKey(),
                    downloaderProperties.getSecretKey()));
        }
        log.info("Setting up S3 async client using AWS Default Credentials Provider");
        return DefaultCredentialsProvider.create();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hedera.mirror.importer.downloader", name = "cloudProvider", havingValue = "GCP")
    S3AsyncClient gcpCloudStorageClient() {
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
                .credentialsProvider(awsCredentialsProvider())
                .region(Region.of(region))
                .httpClient(httpClient)
                .overrideConfiguration(c -> c.addExecutionInterceptor(metricsExecutionInterceptor));
    }
}
