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
import java.util.Map;

public record SpecSetup(
        Map<String, String> features,
        List<Map<String, Object>> accounts,
        List<Map<String, Object>> cryptoAllowances,
        List<Map<String, Object>> entities,
        List<Map<String, Object>> nfts,
        List<Map<String, Object>> tokens,
        List<Map<String, Object>> topicmessages,
        List<Map<String, Object>> transactions) {
}
