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

package com.hedera.mirror.web3.evm.pricing;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatesAndFeesLoaderTest {
    @Mock
    private FileDataRepository fileDataRepository;

    @InjectMocks
    private RatesAndFeesLoader subject;

    private static final long nanos = 1_234_567_890L;
    private static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();

    private static final String CORRUPT_RATES_MESSAGE = "Rates 0.0.112 are corrupt!";
    private static final long EXCHANGE_RATES_ID = 112L;

    private static final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(nanos))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .build();

    private static final String CORRUPT_SCHEDULES_MESSAGE = "Fee schedule 0.0.111 is corrupt!";
    private static final long FEE_SCHEDULES_ID = 111L;

    @Test
    void loadExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(exchangeRatesSet.toByteArray());

        final var actual = subject.loadExchangeRates(nanos);

        assertThat(actual).isEqualTo(exchangeRatesSet);
    }

    @Test
    void loadEmptyExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(EMPTY_BYTE_ARRAY);

        final var actual = subject.loadExchangeRates(nanos);
        assertThat(actual).isEqualTo(ExchangeRateSet.newBuilder().build());
    }

    @Test
    void loadWrongDataExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn("corrupt".getBytes());

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadExchangeRates(nanos));

        assertThat(exception.getMessage()).isEqualTo(CORRUPT_RATES_MESSAGE);
    }

    @Test
    void loadFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(feeSchedules.toByteArray());

        final var actual = subject.loadFeeSchedules(nanos);

        assertThat(actual).isEqualTo(feeSchedules);
    }

    @Test
    void loadEmptyFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(EMPTY_BYTE_ARRAY);

        final var actual = subject.loadFeeSchedules(nanos);
        assertThat(actual).isEqualTo(CurrentAndNextFeeSchedule.newBuilder().build());
    }

    @Test
    void loadWrongDataFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn("corrupt".getBytes());

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadFeeSchedules(nanos));

        assertThat(exception.getMessage()).isEqualTo(CORRUPT_SCHEDULES_MESSAGE);
    }
}
