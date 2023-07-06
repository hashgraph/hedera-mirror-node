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

package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.fees.calculation.UsageBasedFeeCalculator.numSimpleKeys;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

public class PricedUsageCalculator {
    private final UsageAccumulator handleScopedAccumulator = new UsageAccumulator();

    private final AccessorBasedUsages accessorBasedUsages;
    private final OverflowCheckingCalc calculator;

    public PricedUsageCalculator(final AccessorBasedUsages accessorBasedUsages, final OverflowCheckingCalc calculator) {
        this.accessorBasedUsages = accessorBasedUsages;
        this.calculator = calculator;
    }

    public boolean supports(final HederaFunctionality function) {
        return accessorBasedUsages.supports(function);
    }

    public FeeObject inHandleFees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final Store store,
            final HederaEvmContractAliases hederaEvmContractAliases) {
        return fees(accessor, resourcePrices, rate, payerKey, handleScopedAccumulator, store, hederaEvmContractAliases);
    }

    public FeeObject extraHandleFees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final Store store,
            final HederaEvmContractAliases hederaEvmContractAliases) {
        return fees(accessor, resourcePrices, rate, payerKey, new UsageAccumulator(), store, hederaEvmContractAliases);
    }

    private FeeObject fees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final UsageAccumulator accumulator,
            final Store store,
            final HederaEvmContractAliases hederaEvmContractAliases) {

        final var sigUsage = accessor.usageGiven(numSimpleKeys(payerKey));
        accessorBasedUsages.assess(sigUsage, accessor, accumulator, store, hederaEvmContractAliases);
        // We won't take into account congestion pricing that is used in consensus nodes,
        // since we would only simulate transactions and can't replicate the current load of the consensus network,
        // thus we can't calculate a proper multiplier.
        return calculator.fees(accumulator, resourcePrices, rate, 1L);
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }
}
