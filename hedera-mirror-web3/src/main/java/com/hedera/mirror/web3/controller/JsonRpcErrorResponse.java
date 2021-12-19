package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
final class JsonRpcErrorResponse extends JsonRpcResponse {

    private final JsonRpcError error = new JsonRpcError();

    JsonRpcErrorResponse(JsonRpcErrorCode code) {
        this(null, code, null);
    }

    JsonRpcErrorResponse(JsonRpcRequest request, JsonRpcErrorCode code) {
        this(request, code, null);
    }

    JsonRpcErrorResponse(JsonRpcRequest request, JsonRpcErrorCode code, String detailedMessage) {
        String message = code.getMessage();

        if (StringUtils.isNotBlank(detailedMessage)) {
            message += ": " + detailedMessage;
        }

        if (request != null && request.getId() != null && request.getId() >= 0) {
            setId(request.getId());
        }

        error.setCode(code.getCode());
        error.setMessage(message);
    }

    @Data
    public static class JsonRpcError {
        private int code;
        private Object data;
        private String message;
    }
}
