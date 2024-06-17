/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.rest-java")
public class RestJavaProperties {

    @Min(0)
    private long shard = 0L;

    @NotNull
    private ResponseConfig response = new ResponseConfig();

    @Data
    @Validated
    public static class ResponseConfig {
        @NotNull
        private ResponseHeadersConfig headers = new ResponseHeadersConfig();
    }

    @Data
    @Validated
    public static class ResponseHeadersConfig {
        @NotNull
        private Map<String, String> defaults = new HashMap<>();

        @NotNull
        private Map<String, Map<String, String>> path = new HashMap<>();

        public Map<String, String> getHeadersForPath(String apiPath) {
            return apiPath == null ? defaults : path.getOrDefault(apiPath, defaults);
        }
    }
}
