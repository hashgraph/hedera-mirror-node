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
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.txn.token.CreateLogic.FeeType;
import org.hyperledger.besu.datatypes.Address;

public class CustomFeeUtils {

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

    public static void nullCustomFeeCollectors(CustomFee customFee) {
        final var feeType = getFeeType(customFee);
        switch (feeType) {
            case FIXED_FEE -> {
                final var oldFixedFee = customFee.getFixedFee();
                final var newFixedFee = new FixedFee(
                        oldFixedFee.getAmount(),
                        oldFixedFee.getDenominatingTokenId(),
                        oldFixedFee.isUseHbarsForPayment(),
                        oldFixedFee.isUseHbarsForPayment(),
                        null);
                customFee.setFixedFee(newFixedFee);
            }
            case FRACTIONAL_FEE -> {
                final var oldFractionalFee = customFee.getFractionalFee();
                final var newFractionalFee = new FractionalFee(
                        oldFractionalFee.getNumerator(),
                        oldFractionalFee.getDenominator(),
                        oldFractionalFee.getMinimumAmount(),
                        oldFractionalFee.getMaximumAmount(),
                        oldFractionalFee.getNetOfTransfers(),
                        null);
                customFee.setFractionalFee(newFractionalFee);
            }
            case ROYALTY_FEE -> {
                final var oldRoyaltyFee = customFee.getRoyaltyFee();
                final var newRoyaltyFee = new RoyaltyFee(
                        oldRoyaltyFee.getNumerator(),
                        oldRoyaltyFee.getDenominator(),
                        oldRoyaltyFee.getAmount(),
                        oldRoyaltyFee.getDenominatingTokenId(),
                        oldRoyaltyFee.isUseHbarsForPayment(),
                        null);
                customFee.setRoyaltyFee(newRoyaltyFee);
            }
        }
    }
}
