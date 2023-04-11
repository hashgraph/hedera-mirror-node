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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatesAndFeesLoaderTest {
    @Mock
    private FileDataRepository fileDataRepository;

    private RatesAndFeesLoader subject;

    @BeforeEach
    void setup() {
        subject = new RatesAndFeesLoader(fileDataRepository);
    }

    @Test
    void loadExchangeRates() {
        final var exchangeRates = ExchangeRateSet.newBuilder().build();
        when(fileDataRepository.getFileAtTimestamp(eq(112L) ,anyLong())).thenReturn(exchangeRates.toByteArray());

        final var actual = subject.loadExchangeRates(1L);

        assertEquals(exchangeRates, actual);
    }

    @Test
    void loadFeeSchedules() {
        final var feeSchedules = CurrentAndNextFeeSchedule.newBuilder().build();
        when(fileDataRepository.getFileAtTimestamp(eq(111L), anyLong())).thenReturn(feeSchedules.toByteArray());

        final var actual = subject.loadFeeSchedules(1L);

        assertEquals(feeSchedules, actual);
    }
}
