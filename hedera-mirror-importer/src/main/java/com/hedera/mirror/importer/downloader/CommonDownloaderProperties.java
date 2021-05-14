package com.hedera.mirror.importer.downloader;

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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.MirrorProperties;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.downloader")
public class CommonDownloaderProperties {

    private final MirrorProperties mirrorProperties;

    private String accessKey;

    private String bucketName;

    public String getBucketName() {
        return StringUtils.isNotBlank(bucketName) ? bucketName : mirrorProperties.getNetwork().getBucketName();
    }

    @Max(1)
    @Min(0)
    private float consensusRatio = 0.333f;

    @NotNull
    private CloudProvider cloudProvider = CloudProvider.S3;

    private String endpointOverride;

    @Min(0)
    private int maxConcurrency = 1000; // aws sdk default = 50

    private String region = "us-east-1";

    private String secretKey;

    private String gcpProjectId;

    private Boolean allowAnonymousAccess;

    public boolean isStaticCredentials() {
        //If the cloud provider is GCP, it must use the static provider.  If the static credentials are both present,
        //force the mirror node to use the static provider.
        return cloudProvider == CommonDownloaderProperties.CloudProvider.GCP ||
                (StringUtils.isNotBlank(accessKey) && StringUtils
                        .isNotBlank(secretKey));
    }

    public boolean isAnonymousCredentials() {
        return allowAnonymousAccess != null ? allowAnonymousAccess : mirrorProperties.getNetwork()
                .getAllowAnonymousAccess();
    }

    @Getter
    @RequiredArgsConstructor
    public enum CloudProvider {
        S3("https://s3.amazonaws.com"),
        GCP("https://storage.googleapis.com");

        private final String endpoint;
    }
}
