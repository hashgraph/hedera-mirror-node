/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

package com.hedera.services.fees.calculation;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicFcfsUsagePricesTest {
    private final long currentExpiry = 1_234_567;
    private final long nextExpiry = currentExpiry + 1_000;
    private final FeeComponents currResourceUsagePrices = FeeComponents.newBuilder()
            .setMin(currentExpiry)
            .setMax(currentExpiry)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private final FeeComponents nextResourceUsagePrices = FeeComponents.newBuilder()
            .setMin(nextExpiry)
            .setMax(nextExpiry)
            .setBpr(2_000_000L)
            .setBpt(3_000_000L)
            .setRbh(4_000_000L)
            .setSbh(5_000_000L)
            .build();
    private final FeeData currUsagePrices = FeeData.newBuilder()
            .setNetworkdata(currResourceUsagePrices)
            .setNodedata(currResourceUsagePrices)
            .setServicedata(currResourceUsagePrices)
            .build();
    private final FeeData nextUsagePrices = FeeData.newBuilder()
            .setNetworkdata(nextResourceUsagePrices)
            .setNodedata(nextResourceUsagePrices)
            .setServicedata(nextResourceUsagePrices)
            .build();

    private final Map<SubType, FeeData> currUsagePricesMap = Map.of(DEFAULT, currUsagePrices);
    private final Map<SubType, FeeData> nextUsagePricesMap = Map.of(DEFAULT, nextUsagePrices);

    private final Map<SubType, FeeData> nextContractCallPrices = nextUsagePricesMap;
    private final Map<SubType, FeeData> currentContractCallPrices = currUsagePricesMap;

    private BasicFcfsUsagePrices subject;

    @Mock
    private RatesAndFeesLoader ratesAndFeesLoader;

    private final FeeSchedule nextFeeSchedule = FeeSchedule.newBuilder()
            .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(nextExpiry))
            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                    .setHederaFunctionality(ContractCall)
                    .addFees(nextContractCallPrices.get(DEFAULT)))
            .build();

    private final FeeSchedule currentFeeSchedule = FeeSchedule.newBuilder()
            .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(currentExpiry))
            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                    .setHederaFunctionality(ContractCall)
                    .addFees(currentContractCallPrices.get(DEFAULT)))
            .build();

    private final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(currentFeeSchedule)
            .setNextFeeSchedule(nextFeeSchedule)
            .build();

    @BeforeEach
    void setup() {
        subject = new BasicFcfsUsagePrices(ratesAndFeesLoader);

        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);
    }

    @Test
    void updatesPricesWhenDefaultCalled() {
        // given:
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        subject.defaultPricesGiven(ContractCall, at);

        // then:
        verify(ratesAndFeesLoader).loadFeeSchedules(at.getSeconds());
    }

    @Test
    void getsTransferUsagePricesAtCurrent() {
        // given:
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        final FeeData actual = subject.defaultPricesGiven(ContractCall, at);

        // then:
        assertEquals(currUsagePrices, actual);
    }

    @Test
    void getsTransferUsagePricesAtNext() {
        // given:
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry + 1).build();

        // when:
        final FeeData actual = subject.defaultPricesGiven(ContractCall, at);

        // then:
        assertEquals(nextUsagePrices, actual);
    }
}
