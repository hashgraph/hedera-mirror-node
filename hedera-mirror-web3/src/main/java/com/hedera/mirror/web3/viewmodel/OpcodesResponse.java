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

package com.hedera.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record OpcodesResponse(
        String address,
        @JsonProperty("contract_id") String contractId,
        long gas,
        boolean failed,
        @JsonProperty("return_value") String returnValue,
        List<Opcode> opcodes
) {

    @Builder
    public record Opcode(
            int pc,
            String op,
            long gas,
            @JsonProperty("gas_cost") long gasCost,
            int depth,
            List<String> stack,
            List<String> memory,
            Map<String, String> storage,
            String reason) {
    }
}
