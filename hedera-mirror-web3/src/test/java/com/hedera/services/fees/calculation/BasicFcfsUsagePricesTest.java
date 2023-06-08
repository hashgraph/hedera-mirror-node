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

package com.hedera.services.fees.calculation;

import static com.hedera.services.fees.calculation.BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UNRECOGNIZED;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicFcfsUsagePricesTest {
    private final long currentExpiry = 1_234_567;
    private final long nextExpiry = currentExpiry + 1_000;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    TxnAccessor accessor;

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

    private BasicFcfsUsagePrices subject;

    @Mock
    private RatesAndFeesLoader ratesAndFeesLoader;

    private final FeeSchedule nextFeeSchedule = FeeSchedule.newBuilder()
            .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(nextExpiry))
            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                    .setHederaFunctionality(ContractCall)
                    .addFees(nextUsagePricesMap.get(DEFAULT)))
            .build();

    private final FeeSchedule currentFeeSchedule = FeeSchedule.newBuilder()
            .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(currentExpiry))
            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                    .setHederaFunctionality(ContractCall)
                    .addFees(currUsagePricesMap.get(DEFAULT)))
            .build();

    private final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(currentFeeSchedule)
            .setNextFeeSchedule(nextFeeSchedule)
            .build();
    TransactionBody contractCallTxnNext = TransactionBody.newBuilder()
            .setTransactionID(TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(currentExpiry + 1)))
            .setContractCall(ContractCallTransactionBody.newBuilder().setContractID(asContract("1.2.3")))
            .build();
    TransactionBody contractCallTxnCurr = TransactionBody.newBuilder()
            .setTransactionID(TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(currentExpiry - 1)))
            .setContractCall(ContractCallTransactionBody.newBuilder().setContractID(asContract("1.2.3")))
            .build();

    @BeforeEach
    void setup() {
        subject = new BasicFcfsUsagePrices(ratesAndFeesLoader);
    }

    @Test
    void updatesPricesWhenDefaultCalled() {
        // when:
        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        subject.defaultPricesGiven(ContractCall, at);

        // then:
        verify(ratesAndFeesLoader).loadFeeSchedules(DomainUtils.timestampInNanosMax(at));
    }

    @Test
    void getsTransferUsagePricesAtCurrent() {
        // when:
        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        final FeeData actual = subject.defaultPricesGiven(ContractCall, at);

        // then:
        assertEquals(currUsagePrices, actual);
    }

    @Test
    void getsTransferUsagePricesAtNext() {
        // when:
        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry + 1).build();

        // when:
        final FeeData actual = subject.defaultPricesGiven(ContractCall, at);

        // then:
        assertEquals(nextUsagePrices, actual);
    }

    @Test
    void usesDefaultPricesForNoFunctionUsagePrices() {
        final Timestamp at =
                Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        final var prices = subject.pricesGiven(ContractCreate, at, feeSchedules);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, prices);
    }

    @Test
    void usesDefaultPricesForUnexpectedFailure() {
        // when
        when(accessor.getFunction()).thenThrow(IllegalStateException.class);

        // when:
        final var prices = subject.activePrices(accessor);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, prices);
    }

    @Test
    void getsActivePricesCurr() {
        // when
        when(accessor.getTxnId()).thenReturn(contractCallTxnCurr.getTransactionID());
        when(accessor.getFunction()).thenReturn(ContractCall);
        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);

        // when:
        final Map<SubType, FeeData> prices = subject.activePrices(accessor);

        // then:
        assertEquals(currUsagePricesMap, prices);
    }

    @Test
    void getsActivePricesNext() {
        // when
        when(accessor.getTxnId()).thenReturn(contractCallTxnNext.getTransactionID());
        when(accessor.getFunction()).thenReturn(ContractCall);
        when(ratesAndFeesLoader.loadFeeSchedules(anyLong())).thenReturn(feeSchedules);

        // when:
        final Map<SubType, FeeData> prices = subject.activePrices(accessor);

        // then:
        assertEquals(nextUsagePricesMap, prices);
    }

    @Test
    void getsDefaultPricesIfActiveTxnInvalid() {
        // when
        when(accessor.getFunction()).thenReturn(UNRECOGNIZED);

        // when:
        final Map<SubType, FeeData> prices = subject.activePrices(accessor);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, prices);
    }
}
