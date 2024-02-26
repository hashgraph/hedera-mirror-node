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

package com.hedera.mirror.test.e2e.acceptance.response;

import com.hedera.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;

/**
 * This is a class that represents a response either from the consensus node (with initialized transactionId,
 * networkResponse, errorMessage) or from the mirror node (with initialized contractCallResponse).
 * This is needed for the NetworkAdapter logic.
 */
public record GeneralContractExecutionResponse(
        String transactionId,
        NetworkTransactionResponse networkResponse,
        String errorMessage,
        ContractCallResponseWrapper contractCallResponse) {
    public GeneralContractExecutionResponse(ContractCallResponseWrapper contractCallResponse) {
        this(null, null, null, contractCallResponse);
    }

    public GeneralContractExecutionResponse(
            String transactionId, NetworkTransactionResponse networkResponse, String errorMessage) {
        this(transactionId, networkResponse, errorMessage, null);
    }

    public GeneralContractExecutionResponse(String errorMessage) {
        this(null, null, errorMessage, null);
    }

    public GeneralContractExecutionResponse(String transactionId, String errorMessage) {
        this(transactionId, null, errorMessage, null);
    }
}
