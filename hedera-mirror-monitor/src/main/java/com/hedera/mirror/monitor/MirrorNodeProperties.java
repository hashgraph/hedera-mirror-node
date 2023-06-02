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

package com.hedera.mirror.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class MirrorNodeProperties {

    @NotNull
    private GrpcProperties grpc = new GrpcProperties();

    @NotNull
    private RestProperties rest = new RestProperties();

    @Data
    @Validated
    public static class GrpcProperties {

        @NotBlank
        private String host;

        @Min(0)
        @Max(65535)
        private int port = 443;

        public String getEndpoint() {
            if (host.startsWith("in-process:")) {
                return host;
            }
            return host + ":" + port;
        }
    }

    @Data
    @Validated
    public static class RestProperties {

        @NotBlank
        private String host;

        @Min(0)
        @Max(65535)
        private int port = 443;

        public String getBaseUrl() {
            String scheme = port == 443 ? "https://" : "http://";
            return scheme + host + ":" + port + "/api/v1";
        }
    }
}
