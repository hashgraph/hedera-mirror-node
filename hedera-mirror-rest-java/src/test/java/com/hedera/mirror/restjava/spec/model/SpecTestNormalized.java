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
package com.hedera.mirror.restjava.spec.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Defines a normalized spec test. Given the slightly different formats of the original REST module JSON files
 * (largely around the seemingly optional "tests" array and the use of "url" vs "urls"), this class represents a
 * valid instance of a single test to be contained by {@link RestSpecNormalized}.
 *
 * @param urls the API URL(s) to be invoked
 * @param responseStatus expected integer HTTP status code expected upon success
 * @param responseJson JSON response expected from the API upon success
 */
public record SpecTestNormalized(
        List<String> urls,
        int responseStatus,
        String responseJson) {

    public SpecTestNormalized {
        if (CollectionUtils.isEmpty(urls)) {
            throw new IllegalArgumentException("At least one url is required");
        }
    }

    static SpecTestNormalized from(SpecTest specTest) {
        List<String> urls;
        if (StringUtils.hasText(specTest.url())) {
            List<String> mutableUrls = CollectionUtils.isEmpty(specTest.urls())
                    ? new ArrayList<>()
                    : new ArrayList<>(specTest.urls());

            mutableUrls.add(specTest.url());
            urls = List.copyOf(mutableUrls);
        }
        else {
            urls = specTest.urls() == null ? List.of() : List.copyOf(specTest.urls());
        }

        return new SpecTestNormalized(urls, specTest.responseStatus(), specTest.responseJson());
    }

    public static List<SpecTestNormalized> allFrom(List<SpecTest> specTests) {
        if (CollectionUtils.isEmpty(specTests)) {
            return List.of();
        }

        return specTests.stream().map(SpecTestNormalized::from).toList();
    }
}
