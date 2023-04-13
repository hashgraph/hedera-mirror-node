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

package com.hedera.services.fees;

import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

/**
 * Temporary extracted class from services.
 */
@Named
@RequiredArgsConstructor
public final class BasicHbarCentExchange implements HbarCentExchange {
    private ExchangeRateSet exchangeRates;

    private final RatesAndFeesLoader ratesAndFeesLoader;

    @Override
    public ExchangeRate rate(final Timestamp now) {
        exchangeRates = ratesAndFeesLoader.loadExchangeRates(now.getSeconds());
        return rateAt(now.getSeconds());
    }

    @Override
    public ExchangeRate activeRate(final Instant now) {
        return rateAt(now.getEpochSecond());
    }

    private ExchangeRate rateAt(final long now) {
        final var currentRate = exchangeRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : exchangeRates.getNextRate();
    }
}
