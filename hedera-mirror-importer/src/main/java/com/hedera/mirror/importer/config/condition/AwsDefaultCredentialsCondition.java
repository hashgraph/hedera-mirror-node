package com.hedera.mirror.importer.config.condition;

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

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

public class AwsDefaultCredentialsCondition implements Condition {

    // The roleArn must be provided, and the cloudProvider must be S3 to use AssumeRole
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String accessKey = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.accessKey");
        String secretKey = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.secretKey");
        String cloudProvider = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.cloudProvider");
        if (cloudProvider == null) {
            cloudProvider = CommonDownloaderProperties.CloudProvider.S3.name();
        }
        return (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey))
                && StringUtils.equals(CommonDownloaderProperties.CloudProvider.S3.name(),
                cloudProvider);
    }
}
