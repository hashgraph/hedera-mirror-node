/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class MirrorActionsResponse {
    private List<Action> actions;

    @Data
    public static class Action {
        @JsonProperty("call_depth")
        private int callDepth;

        @JsonProperty("call_operation_type")
        private String callOperationType;

        @JsonProperty("call_type")
        private String callType;

        private String caller;

        @JsonProperty("caller_type")
        private String callerType;

        private String from;
        private long gas;

        @JsonProperty("gas_used")
        private long gasUsed;

        private int index;
        private String input;

        private String recipient;

        @JsonProperty("recipient_type")
        private String recipientType;

        @JsonProperty("result_data")
        private String resultData;

        @JsonProperty("result_data_type")
        private String resultDataType;

        private String timestamp;
        private String to;
        private long value;
    }
}