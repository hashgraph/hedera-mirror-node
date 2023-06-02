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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;

@Data
public class StreamSourceProperties {

    @DurationMin(seconds = 5)
    @NotNull
    private Duration backoff = Duration.ofSeconds(60L);

    @DurationMin(seconds = 1)
    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(5L);

    private SourceCredentials credentials;

    @Min(0)
    private int maxConcurrency = 1000; // aws sdk default = 50

    private String projectId;

    @NotNull
    private String region = "us-east-1";

    @NotNull
    private CommonDownloaderProperties.SourceType type;

    private URI uri;

    /*
     * If the cloud provider is GCP, it must use the static provider.  If the static credentials are both present,
     * force the mirror node to use the static provider.
     */
    public boolean isStaticCredentials() {
        return type == CommonDownloaderProperties.SourceType.GCP || credentials != null;
    }

    @Data
    public static class SourceCredentials {

        @NotBlank
        private String accessKey;

        @NotBlank
        private String secretKey;
    }
}
