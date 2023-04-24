package com.hedera.services.fees.calculation.utils;

import com.hedera.services.fees.annotations.GenericPriceMultiplier;
import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.congestion.FeeMultiplierSource;
import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeObject;

import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.TxnAccessor;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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


    public static void visitSimpleKeys(final JKey key, final Consumer<JKey> actionOnSimpleKey) {
//        if (key.hasThresholdKey()) {
//            key.getThresholdKey().getKeys().getKeysList().forEach(k -> visitSimpleKeys(k, actionOnSimpleKey));
//        } else if (key.hasKeyList()) {
//            key.getKeyList().getKeysList().forEach(k -> visitSimpleKeys(k, actionOnSimpleKey));
//        } else {
//            actionOnSimpleKey.accept(key);
//        }
        //TODO
        actionOnSimpleKey.accept(key);
    }

    /**
     * Counts the simple keys present in a complex Hedera key.
     *
     * @param key the top-level Hedera key.
     * @return the number of simple keys in the leaves of the Hedera key.
     */
    public static int numSimpleKeys(final JKey key) {
        final var count = new AtomicInteger(0);
        visitSimpleKeys(key, ignore -> count.incrementAndGet());
        return count.get();
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }

}
