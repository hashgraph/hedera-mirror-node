/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.fees;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicHbarCentExchangeTest {
    private static final long CROSSOVER_TIME = 1_234_567L;
    private static final ExchangeRateSet RATES = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setHbarEquiv(1)
                    .setCentEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(CROSSOVER_TIME)))
            .setNextRate(ExchangeRate.newBuilder()
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(CROSSOVER_TIME * 2))
                    .setHbarEquiv(1)
                    .setCentEquiv(24))
            .build();

    private BasicHbarCentExchange subject;

    @Mock
    private RatesAndFeesLoader ratesAndFeesLoader;

    @BeforeEach
    void setUp() {
        when(ratesAndFeesLoader.loadExchangeRates(anyLong())).thenReturn(RATES);

        subject = new BasicHbarCentExchange(ratesAndFeesLoader);
    }

    @Test
    void updatesRatesWhenRatesCalled() {
        subject.rate(beforeCrossTime);

        verify(ratesAndFeesLoader).loadExchangeRates(DomainUtils.timestampInNanosMax(beforeCrossTime));
    }

    @Test
    void returnsCurrentRatesWhenBeforeCrossTime() {
        final var result = subject.rate(beforeCrossTime);

        assertEquals(RATES.getCurrentRate(), result);
    }

    @Test
    void returnsNextRatesWhenAfterCrossTime() {
        final var result = subject.rate(afterCrossTime);

        assertEquals(RATES.getNextRate(), result);
    }

    private static final Timestamp beforeCrossTime =
            Timestamp.newBuilder().setSeconds(CROSSOVER_TIME - 1).build();
    private static final Timestamp afterCrossTime =
            Timestamp.newBuilder().setSeconds(CROSSOVER_TIME).build();
}
