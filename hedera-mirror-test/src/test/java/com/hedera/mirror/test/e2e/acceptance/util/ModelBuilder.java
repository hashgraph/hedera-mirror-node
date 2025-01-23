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

package com.hedera.mirror.test.e2e.acceptance.util;

import com.hedera.mirror.rest.model.ContractCallRequest;

/**
 * Utility class for building instances of some of the more involved OpenAPI generated model classes. The previous
 * manually coded POJOs set default values for some fields.
 */
public class ModelBuilder {

    private static final String DEFAULT_CONTRACT_CALL_BLOCK = "latest";
    private static final Boolean DEFAULT_CONTRACT_CALL_ESTIMATE = Boolean.FALSE;
    private static final Long DEFAULT_CONTRACT_CALL_GAS = 1_800_000L;
    private static final Long DEFAULT_CONTRACT_CALL_GAS_PRICE = 100_000_000L;
    private static final Long DEFAULT_CONTRACT_CALL_VALUE = 0L;
    private static final int DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED = 30;

    public static ContractCallRequest contractCallRequest() {
        return new ContractCallRequest()
                .block(DEFAULT_CONTRACT_CALL_BLOCK)
                .estimate(DEFAULT_CONTRACT_CALL_ESTIMATE)
                .gas(DEFAULT_CONTRACT_CALL_GAS)
                .gasPrice(DEFAULT_CONTRACT_CALL_GAS_PRICE)
                .value(DEFAULT_CONTRACT_CALL_VALUE);
    }

    public static ContractCallRequest contractCallRequest(final int actualGasUsed) {
        final Long calculatedContractCallGas =
                Math.round(actualGasUsed * (1 + (DEFAULT_PERCENTAGE_OF_ACTUAL_GAS_USED / 100.0)));
        final ContractCallRequest contractCallRequest = contractCallRequest();
        contractCallRequest.setGas(calculatedContractCallGas);
        return contractCallRequest;
    }
}
