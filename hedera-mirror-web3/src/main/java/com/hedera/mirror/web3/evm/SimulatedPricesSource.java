package com.hedera.mirror.web3.evm;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.function.ToLongFunction;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.fees.calculation.SimulatedFcfsUsagePrices;
import com.hedera.services.transaction.fees.HbarCentExchange;

// FUTURE WORK This should move to an interface when evm-module is complete

/**
 * Provider of gas price related to given consensus timestamp in Instant format. The calculations
 * are performed based on the Fee Schedule and Exchange Rate files saved in the DB
 */
@Singleton
@RequiredArgsConstructor
public class SimulatedPricesSource {
    private static final long DEFAULT_MULTIPLIER = 1L;

    HbarCentExchange exchange;
    SimulatedFcfsUsagePrices usagePrices;

    public long currentGasPrice(final Instant now, final HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    private long currentPrice(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);

        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
        final var curMultiplier = DEFAULT_MULTIPLIER;
        if (curMultiplier > maxMultiplier) {
            return Long.MAX_VALUE;
        } else {
            return unscaledPrice * curMultiplier;
        }
    }

    private long currentFeeInTinycents(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        final var prices = usagePrices.defaultPricesGiven(function, timestamp);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }
}
