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

package com.hedera.mirror.restjava.spec.model;

import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;

/**
 * Defines a normalized spec test. Given the slightly different formats of the original REST module JSON files
 * (largely around the seemingly optional "tests" array and the use of "url" vs "urls"), this class represents a
 * valid instance of a single test to be contained by {@link RestSpecNormalized}.
 *
 * @param responseHeaders expected HTTP headers returned in response
 * @param responseJson JSON response expected from the API upon success
 * @param responseStatus expected integer HTTP status code expected upon success
 * @param urls the API URL(s) to be invoked
 */
public record SpecTestNormalized(
        Map<String, String> responseHeaders, String responseJson, int responseStatus, List<String> urls) {

    public SpecTestNormalized {
        if (CollectionUtils.isEmpty(urls)) {
            throw new IllegalArgumentException("At least one url is required");
        }
    }

    static SpecTestNormalized from(SpecTest specTest) {
        return new SpecTestNormalized(
                specTest.responseHeaders(),
                specTest.responseJson(),
                specTest.responseStatus(),
                specTest.getNormalizedUrls());
    }

    static List<SpecTestNormalized> allFrom(List<SpecTest> specTests) {
        if (CollectionUtils.isEmpty(specTests)) {
            return List.of();
        }
        return specTests.stream().map(SpecTestNormalized::from).toList();
    }
}
