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

package com.hedera.services.utils;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.services.txn.token.CreateLogic.FeeType;
import org.hyperledger.besu.datatypes.Address;

public class CustomFeeUtils {

    private CustomFeeUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Address getFeeCollector(CustomFee customFee) {
        final var type = getFeeType(customFee);
        if (type.equals(FeeType.FIXED_FEE)) {
            return customFee.getFixedFee().getFeeCollector();
        } else if (type.equals(FeeType.FRACTIONAL_FEE)) {
            return customFee.getFractionalFee().getFeeCollector();
        } else {
            return customFee.getRoyaltyFee().getFeeCollector();
        }
    }

    public static FeeType getFeeType(CustomFee customFee) {
        if (customFee.getFixedFee() != null) {
            return FeeType.FIXED_FEE;
        } else if (customFee.getFractionalFee() != null) {
            return FeeType.FRACTIONAL_FEE;
        } else {
            return FeeType.ROYALTY_FEE;
        }
    }
}
