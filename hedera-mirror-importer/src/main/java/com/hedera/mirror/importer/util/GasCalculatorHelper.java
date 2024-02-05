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

package com.hedera.mirror.importer.util;

import com.google.protobuf.ByteString;

public class GasCalculatorHelper {

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;
    private static final long TX_CREATE_EXTRA = 32_000L;

    private GasCalculatorHelper() {}

    /**
     * Adds the intrinsic gas cost to the gas consumed by the EVM.
     * In case of contract deployment, the init code is used to
     * calculate the intrinsic gas cost. Otherwise, it's fixed at 21_000.
     *
     * @param gas The gas consumed by the EVM for the transaction
     * @param initByteCode The init code of the contract
     */
    public static long addIntrinsicGas(final long gas, final ByteString initByteCode) {
        if (initByteCode == null) {
            return gas + TX_BASE_COST;
        }

        int zeros = 0;
        for (int i = 0; i < initByteCode.size(); i++) {
            if (initByteCode.byteAt(i) == 0) {
                ++zeros;
            }
        }
        final int nonZeros = initByteCode.size() - zeros;

        long costForByteCode = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

        return costForByteCode + gas + TX_CREATE_EXTRA;
    }
}
