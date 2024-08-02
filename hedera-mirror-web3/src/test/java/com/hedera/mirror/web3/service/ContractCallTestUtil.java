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

package com.hedera.mirror.web3.service;

public class ContractCallTestUtil {

    public static final long TRANSACTION_GAS_LIMIT = 15_000_000L;

    public static final double GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE = 1.05;
    public static final double GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE = 1.2;

    public static final String ESTIMATE_GAS_ERROR_MESSAGE =
            "Expected gas usage to be within the expected range, but it was not. Estimate: %d, Actual: %d";

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param estimatedGas The expected gas used from the initial call.
     * @param actualGas   The actual gas used.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    public static boolean isWithinExpectedGasRange(final long estimatedGas, final long actualGas) {
        return estimatedGas >= (actualGas * GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE)
                && estimatedGas <= (actualGas * GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE);
    }
}
