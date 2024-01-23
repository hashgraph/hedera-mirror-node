package com.hedera.mirror.test.e2e.acceptance.util;

import com.hedera.mirror.rest.model.ContractCallRequest;

/**
 * Utility class for building instances of some of the more involved OpenAPI generated model classes.
 */
public class ModelBuilder {

    private static final String DEFAULT_CONTRACT_CALL_BLOCK = "latest";
    private static final Long DEFAULT_CONTRACT_CALL_GAS = 15_000_000L;
    private static final Long DEFAULT_CONTRACT_CALL_GAS_PRICE = 100_000_000L;
    private static final Long DEFAULT_CONTRACT_CALL_VALUE = 0L;

    public static ContractCallRequest contractCallRequest(String data, boolean estimate, String toAddress) {
        return new ContractCallRequest()
                .block(DEFAULT_CONTRACT_CALL_BLOCK)
                .data(data)
                .estimate(estimate)
                .gas(DEFAULT_CONTRACT_CALL_GAS)
                .gasPrice(DEFAULT_CONTRACT_CALL_GAS_PRICE)
                .to(toAddress)
                .value(DEFAULT_CONTRACT_CALL_VALUE);
    }

    public static ContractCallRequest contractCallRequest(String data, boolean estimate, String fromAddress, String toAddress) {
        return contractCallRequest(data, estimate, toAddress)
                .from(fromAddress);
    }
}
