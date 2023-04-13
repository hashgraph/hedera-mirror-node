package com.hedera.services.fees.calculation.utils;

import com.hedera.services.fees.congestion.FeeMultiplierSource;
import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeObject;

import com.hedera.services.utils.accessors.TxnAccessor;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import javax.inject.Inject;

public class PricedUsageCalculator {

    private final UsageAccumulator handleScopedAccumulator = new UsageAccumulator();

    private final AccessorBasedUsages accessorBasedUsages;
    private final FeeMultiplierSource feeMultiplierSource;
    private final OverflowCheckingCalc calculator;

    @Inject
    public PricedUsageCalculator(
            final AccessorBasedUsages accessorBasedUsages,
            @GenericPriceMultiplier final FeeMultiplierSource feeMultiplierSource,
            final OverflowCheckingCalc calculator) {
        this.accessorBasedUsages = accessorBasedUsages;
        this.feeMultiplierSource = feeMultiplierSource;
        this.calculator = calculator;
    }

    public boolean supports(final HederaFunctionality function) {
        return accessorBasedUsages.supports(function);
    }

    public FeeObject inHandleFees(
            final TxnAccessor accessor, final FeeData resourcePrices, final ExchangeRate rate, final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, handleScopedAccumulator);
    }

    public FeeObject extraHandleFees(
            final TxnAccessor accessor, final FeeData resourcePrices, final ExchangeRate rate, final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, new UsageAccumulator());
    }

    private FeeObject fees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final UsageAccumulator accumulator) {
        final var sigUsage = accessor.usageGiven(numSimpleKeys(payerKey));

        accessorBasedUsages.assess(sigUsage, accessor, accumulator);

        return calculator.fees(accumulator, resourcePrices, rate, feeMultiplierSource.currentMultiplier(accessor));
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }

}
