package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.StreamSourceProperties;
import com.hedera.mirror.importer.downloader.provider.LocalStreamFileProvider;
import com.hedera.mirror.importer.downloader.provider.S3StreamFileProvider;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;

@Configuration
@CustomLog
@RequiredArgsConstructor
class CloudStorageConfiguration {

    private final CommonDownloaderProperties commonDownloaderProperties;
    private final MetricsExecutionInterceptor metricsExecutionInterceptor;

    @Bean
    List<StreamFileProvider> streamFileProviders() {
        log.info("Configured to download from bucket {}", commonDownloaderProperties.getBucketName());
        var providers = new ArrayList<StreamFileProvider>();

        for (var source : commonDownloaderProperties.getSources()) {
            var provider = switch (source.getType()) {
                case LOCAL -> new LocalStreamFileProvider(commonDownloaderProperties);
                case GCP, S3 -> new S3StreamFileProvider(commonDownloaderProperties, s3Client(source));
            };

            providers.add(provider);
        }

        return providers;
    }

    private AwsCredentialsProvider awsCredentialsProvider(StreamSourceProperties sourceProperties) {
        var type = sourceProperties.getType();

        if (commonDownloaderProperties.isAnonymousCredentials()) {
            log.info("Setting up {} client using anonymous credentials", type);
            return AnonymousCredentialsProvider.create();
        } else if (sourceProperties.isStaticCredentials()) {
            log.info("Setting up {} client using provided access/secret key", type);
            var credentials = sourceProperties.getCredentials();
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.getAccessKey(),
                    credentials.getSecretKey()));
        }

        log.info("Setting up {} client using default credentials provider", type);
        return DefaultCredentialsProvider.create();
    }

    private S3AsyncClient s3Client(StreamSourceProperties sourceProperties) {
        var httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(sourceProperties.getConnectionTimeout())
                .maxConcurrency(sourceProperties.getMaxConcurrency())
                .connectionMaxIdleTime(Duration.ofSeconds(5))  // https://github.com/aws/aws-sdk-java-v2/issues/1122
                .build();

        if (sourceProperties.getUri() == null) {
            sourceProperties.setUri(URI.create(sourceProperties.getType().getEndpoint()));
        }

        return S3AsyncClient.builder()
                .credentialsProvider(awsCredentialsProvider(sourceProperties))
                .endpointOverride(sourceProperties.getUri())
                .forcePathStyle(true)
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfiguration(sourceProperties))
                .region(Region.of(sourceProperties.getRegion()))
                .build();
    }

    private ClientOverrideConfiguration overrideConfiguration(StreamSourceProperties sourceProperties) {
        var projectId = sourceProperties.getProjectId();
        var builder = ClientOverrideConfiguration.builder().addExecutionInterceptor(metricsExecutionInterceptor);

        if (StringUtils.isNotBlank(projectId)) {
            builder.addExecutionInterceptor(new ExecutionInterceptor() {
                @Override
                public SdkHttpRequest modifyHttpRequest(
                        Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
                    return context.httpRequest().toBuilder()
                            .appendRawQueryParameter("userProject", projectId).build();
                }
            });
        }

        return builder.build();
    }
}
