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

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
public class CredentialsProviderConfiguration {

    private final CommonDownloaderProperties downloaderProperties;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
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
}
