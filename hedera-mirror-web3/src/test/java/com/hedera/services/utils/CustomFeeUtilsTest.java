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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.txn.token.CreateLogic.FeeType;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class CustomFeeUtilsTest {

    private final Address feeAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");

    @Test
    void getFeeCollector() {
        var customFee = new CustomFee();
        var fixedFee = new FixedFee(0, null, true, true, feeAddress);
        customFee.setFixedFee(fixedFee);
        var collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);

        customFee = new CustomFee();
        var royaltyFee = new RoyaltyFee(1, 1, 0, null, true, feeAddress);
        customFee.setRoyaltyFee(royaltyFee);
        collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);

        customFee = new CustomFee();
        var fractureFee = new FractionalFee(1, 1, 1, 2, true, feeAddress);
        customFee.setFractionalFee(fractureFee);
        collector = CustomFeeUtils.getFeeCollector(customFee);
        assertEquals(feeAddress, collector);
    }

    @Test
    void getFeeType() {
        var customFee = new CustomFee();
        customFee.setFixedFee(mock(FixedFee.class));
        var feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.FIXED_FEE, feeType);

        customFee = new CustomFee();
        customFee.setRoyaltyFee(mock(RoyaltyFee.class));
        feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.ROYALTY_FEE, feeType);

        customFee = new CustomFee();
        customFee.setFractionalFee(mock(FractionalFee.class));
        feeType = CustomFeeUtils.getFeeType(customFee);
        assertEquals(FeeType.FRACTIONAL_FEE, feeType);
    }
}
