package com.hedera.mirror.downloader;

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

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.inject.Named;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Named
@Validated
@ConfigurationProperties("hedera.mirror.downloader")
public class CommonDownloaderProperties {

    private String accessKey;

    private String secretKey;

    @NotBlank
    private String bucketName;

    @NotNull
    private CloudProvider cloudProvider = CloudProvider.S3;

    @Min(0)
    private int maxConcurrency = 1000; // aws sdk default = 50

    @Min(0)
    private int maxPendingAcquires = 10000; // aws sdk default = 10,000

    private String region = "us-east-1";

    @Getter
    @RequiredArgsConstructor
    public enum CloudProvider {
        S3("https://s3.amazonaws.com"),
        GCP("https://storage.googleapis.com"),
        LOCAL("http://127.0.0.1:8001"); // Testing

        private final String endpoint;
    }
}
