/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.contracts.execution;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.function.ToLongFunction;

/**
 * This class is a modified copy of LivePricesSource from hedera-services repo.
 *
 * Differences with the original:
 *  1. Hardcoded multiplier
 */
public class LivePricesSource implements PricesAndFeesProvider {

    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;

    public LivePricesSource(final HbarCentExchange exchange, final UsagePricesProvider usagePrices) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
    }

    @Override
    public long currentGasPrice(final Instant now, final HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    public long currentGasPriceInTinycents(final Instant now, final HederaFunctionality function) {
        return currentFeeInTinycents(now, function, FeeComponents::getGas);
    }

    private long currentPrice(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);

        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;

        // Hardcoded to 1, since we don't have congestion control in the Archive Node
        final var curMultiplier = 1L;
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
        final var timestamp =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        final var prices = usagePrices.defaultPricesGiven(function, timestamp);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }
}
