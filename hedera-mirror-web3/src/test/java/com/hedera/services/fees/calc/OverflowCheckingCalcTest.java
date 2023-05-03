/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.calc;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.HRS_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeBuilder;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import org.junit.jupiter.api.Test;

class OverflowCheckingCalcTest {
    private static final int rateTinybarComponent = 1001;
    private static final int rateTinycentComponent = 1000;
    private static final ExchangeRate someRate = ExchangeRate.newBuilder()
            .setHbarEquiv(rateTinybarComponent)
            .setCentEquiv(rateTinycentComponent)
            .build();
    private static final OverflowCheckingCalc subject = new OverflowCheckingCalc();

    @Test
    void throwsOnMultiplierOverflow() {
        final var usage = new UsageAccumulator();
        copyData(mockUsage, usage);

        assertThrows(IllegalArgumentException.class, () -> subject.fees(usage, mockPrices, mockRate, Long.MAX_VALUE));
    }

    @Test
    void converterCanFallbackToBigDecimal() {
        final var highFee = Long.MAX_VALUE / rateTinycentComponent;
        final var expectedTinybarFee = FeeBuilder.getTinybarsFromTinyCents(someRate, highFee);

        final long computedTinybarFee = OverflowCheckingCalc.tinycentsToTinybars(highFee, someRate);

        assertEquals(expectedTinybarFee, computedTinybarFee);
    }

    @Test
    void safeAccumulateTwoWorks() {
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateTwo(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateTwo(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateTwo(1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateTwo(1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateTwo(1, 1, Long.MAX_VALUE));

        assertEquals(3, subject.safeAccumulateTwo(1, 1, 1));
    }

    @Test
    void safeAccumulateThreeWorks() {
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(-1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, Long.MAX_VALUE, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, 1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateThree(1, 1, 1, Long.MAX_VALUE));

        assertEquals(4, subject.safeAccumulateThree(1, 1, 1, 1));
    }

    @Test
    void safeAccumulateFourWorks() {
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(-1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, -1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, Long.MAX_VALUE, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, Long.MAX_VALUE, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, 1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> subject.safeAccumulateFour(1, 1, 1, 1, Long.MAX_VALUE));

        assertEquals(5, subject.safeAccumulateFour(1, 1, 1, 1, 1));
    }

    private static final long multiplier = 2L;
    private static final long veryHighFloorFee = Long.MAX_VALUE / 2;
    private static final FeeComponents mockLowCeilFees = FeeComponents.newBuilder()
            .setMax(1234567L)
            .setConstant(1_234_567L)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private static final FeeComponents mockHighFloorFees = FeeComponents.newBuilder()
            .setMin(veryHighFloorFee)
            .setConstant(1_234_567L)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private static final FeeComponents mockFees = FeeComponents.newBuilder()
            .setMax(Long.MAX_VALUE)
            .setConstant(1_234_567L)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private static final ExchangeRate mockRate =
            ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(120).build();

    private static final FeeData mockPrices = FeeData.newBuilder()
            .setNetworkdata(mockFees)
            .setNodedata(mockFees)
            .setServicedata(mockFees)
            .build();
    private static final FeeData mockLowCeilPrices = FeeData.newBuilder()
            .setNetworkdata(mockLowCeilFees)
            .setNodedata(mockLowCeilFees)
            .setServicedata(mockLowCeilFees)
            .build();
    private static final FeeData mockHighFloorPrices = FeeData.newBuilder()
            .setNetworkdata(mockHighFloorFees)
            .setNodedata(mockHighFloorFees)
            .setServicedata(mockHighFloorFees)
            .build();

    private static final long one = 1;
    private static final long bpt = 2;
    private static final long vpt = 3;
    private static final long rbh = 4;
    private static final long sbh = 5;
    private static final long bpr = 8;
    private static final long sbpr = 9;
    private static final long network_rbh = 10;
    private static final FeeComponents mockUsageVector = FeeComponents.newBuilder()
            .setConstant(one)
            .setBpt(bpt)
            .setVpt(vpt)
            .setRbh(rbh)
            .setSbh(sbh)
            .setBpr(bpr)
            .setSbpr(sbpr)
            .build();
    private static final FeeData mockUsage =
            ESTIMATOR_UTILS.withDefaultTxnPartitioning(mockUsageVector, SubType.DEFAULT, network_rbh, 3);

    private static final void copyData(final FeeData feeData, final UsageAccumulator into) {
        into.setNumPayerKeys(feeData.getNodedata().getVpt());
        into.addVpt(feeData.getNetworkdata().getVpt());
        into.addBpt(feeData.getNetworkdata().getBpt());
        into.addBpr(feeData.getNodedata().getBpr());
        into.addSbpr(feeData.getNodedata().getSbpr());
        into.addNetworkRbs(feeData.getNetworkdata().getRbh() * HRS_DIVISOR);
        into.addRbs(feeData.getServicedata().getRbh() * HRS_DIVISOR);
        into.addSbs(feeData.getServicedata().getSbh() * HRS_DIVISOR);
    }
}
