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

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import com.hedera.mirror.importer.config.condition.AwsAssumeRoleCondition;
import com.hedera.mirror.importer.config.condition.StaticCredentialsCondition;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.exception.MissingCredentialsException;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
public class CredentialsProviderConfiguration {

    private final CommonDownloaderProperties downloaderProperties;

    @Bean
    @Conditional(StaticCredentialsCondition.class)
    public AwsCredentialsProvider staticCredentialsProvider() {
        log.info("Setting up S3 async client using provided access/secret key");
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(downloaderProperties.getAccessKey(),
                downloaderProperties.getSecretKey()));
    }

    @Bean
    @Conditional(AwsAssumeRoleCondition.class)
    public AwsCredentialsProvider stsAssumeRoleCredentialsProvider() {
        log.info("Setting up S3 async client using temporary credentials (AWS AssumeRole)");
        if (StringUtils.isBlank(downloaderProperties.getAccessKey())
                || StringUtils.isBlank(downloaderProperties.getSecretKey())) {
            throw new MissingCredentialsException("Cannot connect to S3 using AssumeRole without user keys");
        }

        StsClient stsClient = StsClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        downloaderProperties.getAccessKey(), downloaderProperties.getSecretKey())))
                .region(Region.of(downloaderProperties.getRegion()))
                .build();

        AssumeRoleRequest.Builder assumeRoleRequestBuilder = AssumeRoleRequest.builder()
                .roleArn(downloaderProperties.getS3().getRoleArn())
                .roleSessionName(downloaderProperties.getS3().getRoleSessionName());

        if (StringUtils.isNotBlank(downloaderProperties.getS3().getExternalId())) {
            assumeRoleRequestBuilder.externalId(downloaderProperties.getS3().getExternalId());
        }

        return StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient)
                .refreshRequest(assumeRoleRequestBuilder.build())
                .build();
    }

    //This should only be created when none of the conditions to create an AwsCredentialsProvider are met.
    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider anonymousCredentialsProvider() {
        log.info("Setting up S3 async client using anonymous credentials");
        return AnonymousCredentialsProvider.create();
    }
}
