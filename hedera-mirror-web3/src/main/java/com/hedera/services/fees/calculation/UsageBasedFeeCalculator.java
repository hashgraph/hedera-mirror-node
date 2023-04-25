package com.hedera.services.fees.calculation;

import static com.hedera.services.fees.calculation.utils.PricedUsageCalculator.numSimpleKeys;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.jproto.JKey;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.annotations.GenericPriceMultiplier;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.fees.congestion.FeeMultiplierSource;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.utils.accessors.TxnAccessor;

public class UsageBasedFeeCalculator implements FeeCalculator {
    private static final Logger log = LogManager.getLogger(UsageBasedFeeCalculator.class);

    private final HbarCentExchange exchange;
    private final FeeMultiplierSource feeMultiplierSource;
    private final UsagePricesProvider usagePrices;
    private final PricedUsageCalculator pricedUsageCalculator;
    private final List<QueryResourceUsageEstimator> queryUsageEstimators;
    private final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;

    @Inject
    public UsageBasedFeeCalculator(
            final HbarCentExchange exchange,
            final UsagePricesProvider usagePrices,
            final @GenericPriceMultiplier FeeMultiplierSource feeMultiplierSource,
            final PricedUsageCalculator pricedUsageCalculator,
            final Set<QueryResourceUsageEstimator> queryUsageEstimators,
            final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.feeMultiplierSource = feeMultiplierSource;
        this.txnUsageEstimators = txnUsageEstimators;
        this.queryUsageEstimators = new ArrayList<>(queryUsageEstimators);
        this.pricedUsageCalculator = pricedUsageCalculator;
    }

    @Override
    public FeeObject estimatePayment(
            Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type) {
        return compute(query, usagePrices, at, estimator -> estimator.usageGivenType(query, view, type));
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = exchange.rate(at);
        var prices = usagePrices.defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    @Override
    public FeeObject computeFee(TxnAccessor accessor, JKey payerKey, StateView view, Timestamp at) {
        return feeGiven(accessor, payerKey, view, usagePrices.pricesGiven(accessor.getFunction(), at, null), exchange.rate(at), true);
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    private FeeObject compute(
            Query query, FeeData usagePrices, Timestamp at, Function<QueryResourceUsageEstimator, FeeData> usageFn) {
        var usageEstimator = getQueryUsageEstimator(query);
        var queryUsage = usageFn.apply(usageEstimator);
        return computeFromQueryResourceUsage(queryUsage, usagePrices, at);
    }

    /**
     * Computes the fees for a query, given the query's resource usage, the current prices,
     * and the estimated consensus time.
     *
     * @param queryUsage the resource usage of the query
     * @param usagePrices the current prices
     * @param at the estimated consensus time
     * @return the fees for the query
     */
    public FeeObject computeFromQueryResourceUsage(
            final FeeData queryUsage, final FeeData usagePrices, final Timestamp at) {
        return getFeeObject(usagePrices, queryUsage, exchange.rate(at), 1L);
    }

    private QueryResourceUsageEstimator getQueryUsageEstimator(Query query) {
        for (final QueryResourceUsageEstimator estimator : queryUsageEstimators) {
            if (estimator.applicableTo(query)) {
                return estimator;
            }
        }
        throw new NoSuchElementException("No estimator exists for the given query");
    }

    public static FeeObject getFeeObject(
            final FeeData feeData, final FeeData feeMatrices, final ExchangeRate exchangeRate, final long multiplier) {
        // get the Network Fee
        long networkFee = getComponentFeeInTinyCents(feeData.getNetworkdata(), feeMatrices.getNetworkdata());
        long nodeFee = getComponentFeeInTinyCents(feeData.getNodedata(), feeMatrices.getNodedata());
        long serviceFee = getComponentFeeInTinyCents(feeData.getServicedata(), feeMatrices.getServicedata());
        // convert the Fee to tiny hbars
        networkFee = getTinybarsFromTinyCents(exchangeRate, networkFee) * multiplier;
        nodeFee = getTinybarsFromTinyCents(exchangeRate, nodeFee) * multiplier;
        serviceFee = getTinybarsFromTinyCents(exchangeRate, serviceFee) * multiplier;
        return new FeeObject(nodeFee, networkFee, serviceFee);
    }

    /**
     * This method calculates Fee for specific component (Noe/Network/Service) based upon param
     * componentCoefficients and componentMetrics
     *
     * @param componentCoefficients component coefficients
     * @param componentMetrics compnent metrics
     * @return long representation of the fee in tiny cents
     */
    public static long getComponentFeeInTinyCents(
            final FeeComponents componentCoefficients, final FeeComponents componentMetrics) {

        final long bytesUsageFee = componentCoefficients.getBpt() * componentMetrics.getBpt();
        final long verificationFee = componentCoefficients.getVpt() * componentMetrics.getVpt();
        final long ramStorageFee = componentCoefficients.getRbh() * componentMetrics.getRbh();
        final long storageFee = componentCoefficients.getSbh() * componentMetrics.getSbh();
        final long evmGasFee = componentCoefficients.getGas() * componentMetrics.getGas();
        final long txValueFee = Math.round((float) (componentCoefficients.getTv() * componentMetrics.getTv()) / 1000);
        final long bytesResponseFee = componentCoefficients.getBpr() * componentMetrics.getBpr();
        final long storageBytesResponseFee = componentCoefficients.getSbpr() * componentMetrics.getSbpr();
        final long componentUsage = componentCoefficients.getConstant() * componentMetrics.getConstant();

        long totalComponentFee = componentUsage
                + (bytesUsageFee
                + verificationFee
                + ramStorageFee
                + storageFee
                + evmGasFee
                + txValueFee
                + bytesResponseFee
                + storageBytesResponseFee);

        if (totalComponentFee < componentCoefficients.getMin()) {
            totalComponentFee = componentCoefficients.getMin();
        } else if (totalComponentFee > componentCoefficients.getMax()) {
            totalComponentFee = componentCoefficients.getMax();
        }
        return Math.max(totalComponentFee > 0 ? 1 : 0, (totalComponentFee) / FEE_DIVISOR_FACTOR);
    }

    private FeeObject feeGiven(
            final TxnAccessor accessor,
            final JKey payerKey,
            final StateView view,
            final Map<SubType, FeeData> prices,
            final ExchangeRate rate,
            final boolean inHandle) {
        final var function = accessor.getFunction();
        if (pricedUsageCalculator.supports(function)) {
            final var applicablePrices = prices.get(accessor.getSubType());
            return inHandle
                    ? pricedUsageCalculator.inHandleFees(accessor, applicablePrices, rate, payerKey)
                    : pricedUsageCalculator.extraHandleFees(accessor, applicablePrices, rate, payerKey);
        } else {
            var sigUsage = getSigUsage(accessor, payerKey);
            var usageEstimator = getTxnUsageEstimator(accessor);
            try {
                // TODO
                final var usage = usageEstimator.usageGiven(accessor.getTxn(), sigUsage, view);
                final var applicablePrices = prices.get(usage.getSubType());
                return feesIncludingCongestion(usage, applicablePrices, accessor, rate);
            } catch (Exception e) {
                log.warn(
                        "Argument accessor={} malformed for implied estimator {}!",
                        accessor.getSignedTxnWrapper(),
                        usageEstimator);
                throw new IllegalArgumentException(e);
            }
        }
    }

    public FeeObject feesIncludingCongestion(
            final FeeData usage, final FeeData typedPrices, final TxnAccessor accessor, final ExchangeRate rate) {
        return getFeeObject(typedPrices, usage, rate, feeMultiplierSource.currentMultiplier(accessor));
    }

    public SigValueObj getSigUsage(TxnAccessor accessor, JKey payerKey) {
        int numPayerKeys = numSimpleKeys(payerKey);
        final var sigUsage = accessor.usageGiven(numPayerKeys);
        return new SigValueObj(sigUsage.numSigs(), numPayerKeys, sigUsage.sigsSize());
    }


    private TxnResourceUsageEstimator getTxnUsageEstimator(TxnAccessor accessor) {
        var txn = accessor.getTxn();
        var estimators = Optional.ofNullable(txnUsageEstimators.get(accessor.getFunction()))
                .orElse(Collections.emptyList());
        for (TxnResourceUsageEstimator estimator : estimators) {
            if (estimator.applicableTo(txn)) {
                return estimator;
            }
        }
        throw new NoSuchElementException("No estimator exists for the given transaction");
    }
}
