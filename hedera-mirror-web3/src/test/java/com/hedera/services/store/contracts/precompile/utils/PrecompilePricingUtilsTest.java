/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecompilePricingUtilsTest {

    private static final long COST = 36;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    public static final BigDecimal USD_TO_TINYCENTS = BigDecimal.valueOf(100 * 100_000_000L);

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private BasicHbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private BasicFcfsUsagePrices resourceCosts;

    @Mock
    private AccessorFactory accessorFactory;

    @Test
    void failsToLoadCanonicalPrices() throws IOException {
        given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
        assertThrows(
                PrecompilePricingUtils.CanonicalOperationsUnloadableException.class,
                () -> new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory));
    }

    @Test
    void calculatesMinimumPrice() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(exchange.rate(timestamp)).willReturn(exchangeRate);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        final long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE, timestamp);

        assertEquals(
                USD_TO_TINYCENTS
                        .multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE))
                        .longValue(),
                price);
    }
}
