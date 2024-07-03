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

import java.util.List;
import org.springframework.util.CollectionUtils;

/**
 * Represents a normalized REST spec definition, containing at least one test scenario. Given the slightly different
 * formats of the original REST module JSON files (largely around the seemingly optional "tests" array and the use of
 * "url" vs "urls"), those issues have been resolved and are expressed concisely as an instance of this class.
 *
 * @param description
 * @param extendedDescription
 * @param matrix ignored for now
 * @param setup features and other config are not yet supported, just database entity definitions
 * @param tests at least one normalized test
 */
public record RestSpecNormalized(
        String description,
        String extendedDescription,
        String matrix,
        SpecSetup setup,
        List<SpecTestNormalized> tests) {

    public RestSpecNormalized {
        if (CollectionUtils.isEmpty(tests)) {
            throw new IllegalArgumentException("At least one test is required");
        }
    }

    public static RestSpecNormalized from(RestSpec restSpec) {
        List<SpecTestNormalized> normalizedTests;
        if (CollectionUtils.isEmpty(restSpec.tests())) {
            var specTest = new SpecTest(restSpec.url(), restSpec.urls(), restSpec.responseStatus(), restSpec.responseJson());
            normalizedTests = List.of(SpecTestNormalized.from(specTest));
        }
        else {
            normalizedTests = SpecTestNormalized.allFrom(restSpec.tests());
        }
        return new RestSpecNormalized(
                restSpec.description(),
                restSpec.extendedDescription(),
                restSpec.matrix(),
                restSpec.setup(),
                normalizedTests);
    }
}
