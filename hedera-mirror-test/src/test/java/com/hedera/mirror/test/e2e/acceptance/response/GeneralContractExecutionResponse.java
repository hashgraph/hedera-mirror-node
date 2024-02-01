package com.hedera.mirror.test.e2e.acceptance.response;

import com.hedera.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;

/**
 * This is a class that represents a response either from the consensus node (with initialized transactionId,
 * networkResponse, errorMessage) or from the mirror node (with initialized contractCallResponse).
 * This is needed for the NetworkAdapter logic.
 */
public record GeneralContractExecutionResponse(String transactionId, NetworkTransactionResponse networkResponse, String errorMessage, ContractCallResponseWrapper contractCallResponse) {
    public GeneralContractExecutionResponse(ContractCallResponseWrapper contractCallResponse) {
        this(null, null, null, contractCallResponse);
    }

    public GeneralContractExecutionResponse(String transactionId, NetworkTransactionResponse networkResponse, String errorMessage) {
        this(transactionId, networkResponse, errorMessage, null);
    }
}