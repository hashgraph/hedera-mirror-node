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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record SpecSetup(
        Map<String, Object> config,
        List<Map<String, Object>> accounts,
        List<Map<String, Object>> contracts,
        List<Map<String, Object>> cryptoAllowances,
        @JsonProperty("cryptotransfers") List<Map<String, Object>> cryptoTransfers,
        List<Map<String, Object>> entities,
        List<Map<String, Object>> entityStakes,
        Map<String, String> features,
        @JsonProperty("filedata") List<Map<String, Object>> fileData,
        @JsonProperty("networkstakes") List<Map<String, Object>> networkStakes,
        List<Map<String, Object>> nfts,
        List<Map<String, Object>> recordFiles,
        List<Map<String, Object>> stakingRewardTransfers,
        @JsonProperty("tokenaccounts") List<Map<String, Object>> tokenAccounts,
        List<Map<String, Object>> tokenAllowances,
        List<Map<String, Object>> tokens,
        @JsonProperty("topicmessages") List<Map<String, Object>> topicMessages,
        List<Map<String, Object>> transactions) {}
