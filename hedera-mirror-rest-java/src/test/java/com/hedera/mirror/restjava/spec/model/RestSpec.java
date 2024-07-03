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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record RestSpec(
        String description,
        String extendedDescription,
        String matrix,
        SpecSetup setup,
        List<SpecTest> tests,
        String url,
        List<String> urls,
        int responseStatus,
        JsonNode responseJson) {
}
