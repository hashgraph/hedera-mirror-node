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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
public class CredentialsProviderConfiguration {

    private final MirrorProperties mirrorProperties;
    private final CommonDownloaderProperties downloaderProperties;

    @Bean
    public AwsCredentialsProvider staticCredentialsProvider() {
        if (useAnonymousCredentialsProvider()) {
            log.info("Setting up S3 async client using anonymous credentials");
            return AnonymousCredentialsProvider.create();
        } else if (useStaticCredentialsProvider()) {
            log.info("Setting up S3 async client using provided access/secret key");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(downloaderProperties.getAccessKey(),
                    downloaderProperties.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    private boolean useStaticCredentialsProvider() {
        //If the cloud provider is GCP, it must use the static provider.  If the static credentials are both present,
        //force the mirror node to use the static provider.
        return StringUtils
                .equals(downloaderProperties.getCloudProvider().name(), CommonDownloaderProperties.CloudProvider.GCP
                        .name()) ||
                ((StringUtils.isNotBlank(downloaderProperties.getAccessKey()) && StringUtils
                        .isNotBlank(downloaderProperties.getSecretKey())));
    }

    private boolean useAnonymousCredentialsProvider() {
        return downloaderProperties.getAllowAnonymousAccess() != null ? downloaderProperties
                .getAllowAnonymousAccess() : mirrorProperties.getNetwork().getAllowAnonymousAccess();
    }
}
