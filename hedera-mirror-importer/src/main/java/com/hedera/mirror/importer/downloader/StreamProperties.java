package com.hedera.mirror.importer.downloader;

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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.downloader.stream")
public class StreamProperties {

    @NotBlank
    private String bucketName = "hedera-demo-streams";

    private GCSProperties gcs = new GCSProperties();

    private S3Properties s3 = new S3Properties();

    @Min(0)
    private int maxConcurrency = 1000; // aws sdk default = 50

    @Data
    public static class CommonProperties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
    }

    @Data
    @Validated
    @EqualsAndHashCode(callSuper = true)
    public static class S3Properties extends CommonProperties {

        private String region;

        private S3Properties() {
            // Endpoint left null for SDK to generate one automatically based on region and bucket name. Overridden in
            // tests to test with local mockS3.
            region = "us-east-1";
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class GCSProperties extends CommonProperties {

        // Value for x-goog-project-id (GCS interoperability mode). Needed for Requester Pays buckets only.
        private String projectId;

        private GCSProperties() {
            setEndpoint("https://storage.googleapis.com");
        }
    }
}
