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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.rest-java")
public class RestJavaProperties {

    @Min(0)
    private long shard = 0L;

    @Valid
    private ResponseConfig response;

    @Data
    @Validated
    public static class ResponseConfig {
        private ResponseHeadersConfig headers;
    }

    @Data
    @Validated
    public static class ResponseHeadersConfig {
        @NotNull
        @JsonProperty("default")
        private List<ResponseHeader> defaults = List.of(ResponseHeader.DEFAULT_RESPONSE_HEADER);

        private Map<@NotNull String, @NotNull List<ResponseHeader>> path = new HashMap<>();

        public List<ResponseHeader> getHeadersForPath(String path) {
            return path == null ? defaults : this.path.getOrDefault(path, defaults);
        }
    }

    @Data
    @Validated
    @AllArgsConstructor
    public static class ResponseHeader {
        public static final ResponseHeader DEFAULT_RESPONSE_HEADER =
                new ResponseHeader("cache-control", "public, max-age=1");

        @NotBlank
        private String name;

        @NotNull
        private String value;
    }
}
