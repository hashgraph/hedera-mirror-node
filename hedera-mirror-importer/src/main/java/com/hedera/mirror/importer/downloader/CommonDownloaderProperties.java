/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.importer.MirrorProperties;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.downloader")
public class CommonDownloaderProperties {

    private static final MathContext MATH_CONTEXT = new MathContext(19, RoundingMode.DOWN);

    private final MirrorProperties mirrorProperties;

    private String accessKey;

    private Boolean allowAnonymousAccess;

    private int batchSize = 100;

    private String bucketName;

    private SourceType cloudProvider = SourceType.S3;

    @NotNull
    @Max(1)
    @Min(0)
    private BigDecimal consensusRatio = BigDecimal.ONE.divide(BigDecimal.valueOf(3), MATH_CONTEXT);

    private String endpointOverride;

    private String gcpProjectId;

    @DurationMin(seconds = 1)
    @NotNull
    private Duration pathRefreshInterval = Duration.ofSeconds(10L);

    @NotNull
    private PathType pathType = PathType.ACCOUNT_ID;

    private String region = "us-east-1";

    private String secretKey;

    @NotNull
    private List<StreamSourceProperties> sources = new ArrayList<>();

    @Min(1)
    private int threads = 30;

    @DurationMin(seconds = 1)
    @NotNull
    private Duration timeout = Duration.ofSeconds(30L);

    @PostConstruct
    public void init() {
        StreamSourceProperties.SourceCredentials credentials = null;
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            credentials = new StreamSourceProperties.SourceCredentials();
            credentials.setAccessKey(accessKey);
            credentials.setSecretKey(secretKey);
        }

        if (credentials != null || sources.isEmpty()) {
            var source = new StreamSourceProperties();
            source.setCredentials(credentials);
            source.setProjectId(gcpProjectId);
            source.setRegion(region);
            source.setType(cloudProvider);
            if (StringUtils.isNotBlank(endpointOverride)) {
                source.setUri(URI.create(endpointOverride));
            }
            sources.add(0, source);
        }
    }

    public String getBucketName() {
        return StringUtils.isNotBlank(bucketName)
                ? bucketName
                : mirrorProperties.getNetwork().getBucketName();
    }

    public boolean isAnonymousCredentials() {
        return allowAnonymousAccess != null
                ? allowAnonymousAccess
                : mirrorProperties.getNetwork().isAllowAnonymousAccess();
    }

    public enum PathType {
        ACCOUNT_ID,
        AUTO,
        NODE_ID
    }

    @Getter
    @RequiredArgsConstructor
    public enum SourceType {
        GCP("https://storage.googleapis.com"),
        LOCAL(""),
        S3("https://s3.amazonaws.com");

        private final String endpoint;
    }
}
