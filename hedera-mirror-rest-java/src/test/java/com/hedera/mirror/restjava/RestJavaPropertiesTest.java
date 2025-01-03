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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RestJavaPropertiesTest {

    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            "default-header-1", "default value1",
            "Default-Header-2", "default value2");

    private static final Map<String, Map<String, String>> PATH_OVERRIDES = Map.of(
            "path1", Map.of("Default-Header-1", "override value1", "path1-header-1", "path1 value1"),
            "path2",
                    Map.of(
                            "default-header-2",
                            "override value2",
                            "path2-header-1",
                            "path2 value1",
                            "path2-header-2",
                            "path2 value2"));

    /*
     * Headers are merged using a key case insensitive comparator with path specific headers first inheriting
     * from the default headers. So, the header names in the defaults set the map key case even if the value is
     * overridden for a path. This is evident when entries are later iterator over.
     */
    private static final Map<String, Map<String, String>> EXPECTED_MERGE = Map.of(
            "path1",
                    Map.of(
                            "default-header-1",
                            "override value1",
                            "Default-Header-2",
                            "default value2",
                            "path1-header-1",
                            "path1 value1"),
            "path2",
                    Map.of(
                            "default-header-1",
                            "default value1",
                            "Default-Header-2",
                            "override value2",
                            "path2-header-1",
                            "path2 value1",
                            "path2-header-2",
                            "path2 value2"));

    @Test
    void verifyEmptyResponseHeaderMapping() {
        // When
        var properties = new RestJavaProperties();
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersConfig = properties.getResponse().getHeaders();
        assertThat(headersConfig.getDefaults()).isEmpty();
        assertThat(headersConfig.getPath()).isEmpty();
        assertThat(headersConfig.getHeadersForPath(null)).isEmpty();
        assertThat(headersConfig.getHeadersForPath("path1")).isEmpty();
    }

    @ParameterizedTest(name = "Default headers for path {0}")
    @CsvSource({",", "path1", "path2"})
    void verifyDefaultsReturnedForAll(String apiPath) {
        // When
        var properties = new RestJavaProperties();
        var headersConfig = properties.getResponse().getHeaders();
        headersConfig.getDefaults().putAll(DEFAULT_HEADERS);
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersForPath = headersConfig.getHeadersForPath(apiPath);
        assertThat(headersForPath).isEqualTo(DEFAULT_HEADERS);
    }

    @ParameterizedTest(name = "Headers for path {0}")
    @CsvSource({",", "path1", "path2", "path3"})
    void verifyPathOverrides(String apiPath) {
        // When
        var properties = new RestJavaProperties();
        var headersConfig = properties.getResponse().getHeaders();
        headersConfig.getDefaults().putAll(DEFAULT_HEADERS);
        headersConfig.getPath().putAll(PATH_OVERRIDES);
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersForPath = headersConfig.getHeadersForPath(apiPath);
        if (apiPath != null && EXPECTED_MERGE.containsKey(apiPath)) {
            assertThat(headersForPath).isEqualTo(EXPECTED_MERGE.get(apiPath));
        } else {
            assertThat(headersForPath).isEqualTo(DEFAULT_HEADERS);
        }
    }
}
