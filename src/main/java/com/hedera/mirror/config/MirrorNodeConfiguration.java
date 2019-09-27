package com.hedera.mirror.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Request;

import com.amazonaws.Response;

import com.amazonaws.auth.AWSCredentials;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;

import com.amazonaws.auth.BasicAWSCredentials;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import com.hedera.mirror.downloader.CommonDownloaderProperties;

import com.hedera.mirror.downloader.Downloader;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resolver.*;
import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.flywaydb.core.internal.resolver.ResolvedMigrationComparator;
import org.flywaydb.core.internal.resolver.ResolvedMigrationImpl;
import org.flywaydb.core.internal.resolver.java.JavaMigrationResolver;
import org.flywaydb.core.internal.util.ClassUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class MirrorNodeConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "spring.task.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {
    }

    // TODO: Remove in Spring Boot 2.2
    @Bean
    FlywayConfigurationCustomizer flywayCustomizer(ObjectProvider<JavaMigration> javaMigrations) {
        return new FlywayConfigurationCustomizer() {
            @Override
            public void customize(FluentConfiguration configuration) {
                configuration.resolvers(new JavaMigrationResolver(null, null) {
                    @Override
                    public List<ResolvedMigration> resolveMigrations(Context context) {
                        List<ResolvedMigration> resolvedMigrations = new ArrayList<>();

                        for (JavaMigration migration : javaMigrations) {
                            ConfigUtils.injectFlywayConfiguration(migration, configuration);
                            ResolvedMigrationImpl resolvedMigration = extractMigrationInfo(migration);
                            resolvedMigration.setPhysicalLocation(ClassUtils.getLocationOnDisk(migration.getClass()));
                            resolvedMigration.setExecutor(createExecutor(migration));
                            resolvedMigrations.add(resolvedMigration);
                        }

                        Collections.sort(resolvedMigrations, new ResolvedMigrationComparator());
                        return resolvedMigrations;
                    }
                });
            }
        };
    }

    @Bean
    AmazonS3 s3Client(CommonDownloaderProperties downloaderProperties) {
        RetryPolicy retryPolicy = PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(5);
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setRetryPolicy(retryPolicy);
        clientConfiguration.setMaxConnections(downloaderProperties.getMaxConnections());

        RequestHandler2 errorHandler = new RequestHandler2() {
            private Logger logger = LogManager.getLogger(AmazonS3Client.class);

            @Override
            public void afterError(Request<?> request, Response<?> response, Exception e) {
                logger.error("Error calling {} {}", request.getHttpMethod(), request.getEndpoint(), e);
            }
        };

        AWSCredentials awsCredentials = new AnonymousAWSCredentials();

        if (StringUtils.isNotBlank(downloaderProperties.getAccessKey()) &&
                StringUtils.isNotBlank(downloaderProperties.getSecretKey())) {
            awsCredentials = new BasicAWSCredentials(downloaderProperties.getAccessKey(), downloaderProperties
                    .getSecretKey());
        }

        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(downloaderProperties.getCloudProvider().getEndpoint(), downloaderProperties.getRegion()))
                .withRequestHandlers(errorHandler)
                .build();
    }

    @Bean
    public TransferManager transferManager(CommonDownloaderProperties downloaderProperties) {
        TransferManager transferManager = TransferManagerBuilder.standard()
                .withExecutorFactory(() -> new ThreadPoolExecutor(downloaderProperties.getMinThreads(), downloaderProperties
                        .getMaxThreads(),
                        120, TimeUnit.SECONDS, new ArrayBlockingQueue<>(downloaderProperties.getMaxQueued())))
                .withS3Client(s3Client(downloaderProperties))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(transferManager::shutdownNow));
        return transferManager;
    }
}
