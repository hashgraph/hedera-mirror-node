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

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import com.hedera.mirror.downloader.CommonDownloaderProperties;

import org.apache.commons.lang3.StringUtils;
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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public S3AsyncClient s3AsyncClient(CommonDownloaderProperties downloaderProperties) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(downloaderProperties.getMaxConcurrency())
                .connectionMaxIdleTime(Duration.ofSeconds(5))  // https://github.com/aws/aws-sdk-java-v2/issues/1122
                .build();

        AwsCredentialsProvider awsCredentials = AnonymousCredentialsProvider.create();

        String accessKey = downloaderProperties.getAwsAccessKey();
        String secretKey = downloaderProperties.getAwsSecretKey();
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            awsCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        return S3AsyncClient.builder()
                .credentialsProvider(awsCredentials)
                .endpointOverride(URI.create(downloaderProperties.getCloudProvider().getEndpoint()))
                .region(Region.of(downloaderProperties.getRegion()))
                .httpClient(httpClient)
                .build();
    }
}
