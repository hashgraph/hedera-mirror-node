/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.rest-java")
public class RestJavaProperties {

    @NotNull
    @Valid
    private ResponseConfig response = new ResponseConfig();

    @Min(0)
    private long shard = 0L;

    /*
     * Post process the configured response headers. All header names are treated case insensitively, and, for each path,
     * the default headers are first inherited and their values possibly overridden.
     */
    @PostConstruct
    void mergeHeaders() {
        for (var pathHeaders : response.headers.path.entrySet()) {
            var mergedHeaders = Stream.concat(
                            response.headers.defaults.entrySet().stream(), pathHeaders.getValue().entrySet().stream())
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            Entry::getValue,
                            (v1, v2) -> v2,
                            () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

            pathHeaders.setValue(mergedHeaders);
        }
    }

    @Data
    @Validated
    public static class ResponseConfig {
        @NotNull
        @Valid
        private ResponseHeadersConfig headers = new ResponseHeadersConfig();
    }

    @Data
    @Validated
    public static class ResponseHeadersConfig {
        @NotNull
        private Map<String, String> defaults = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        @NotNull
        private Map<String, Map<String, String>> path = new HashMap<>();

        public Map<String, String> getHeadersForPath(String apiPath) {
            return apiPath == null ? defaults : path.getOrDefault(apiPath, defaults);
        }
    }
}
