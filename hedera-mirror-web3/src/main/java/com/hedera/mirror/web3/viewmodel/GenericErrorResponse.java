package com.hedera.mirror.web3.viewmodel;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("_status")
@Value
@NoArgsConstructor
public class GenericErrorResponse {
    List<ErrorMessage> messages = new ArrayList<>();

    public GenericErrorResponse(String message) {
        messages.add(new ErrorMessage(message, StringUtils.EMPTY, StringUtils.EMPTY));
    }

    public GenericErrorResponse(String message, String detailedMessage, String data) {
        final var errorMessage = new ErrorMessage(message, detailedMessage, data);
        messages.add(errorMessage);
    }

    public GenericErrorResponse(List<String> errorMessages) {
        errorMessages.forEach(m -> messages.add(new ErrorMessage(m, StringUtils.EMPTY, StringUtils.EMPTY)));
    }

    @Value
    public static class ErrorMessage {
        private String message;
        private String detail;
        private String data;
    }
}
