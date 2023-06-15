/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.calculation;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getFeeObject;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.jproto.JKey;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements a {@link FeeCalculator} in terms of injected usage prices, exchange rates, and collections of estimators
 * which can infer the resource usage of various transactions and queries.
 *
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Hardcode the FeeMultiplierSource
 *  3. Remove unused methods: init, estimatedNonFeePayerAdjustments, estimateFee, computePayment, assessCryptoAutoRenewal
 */
@Named
public class UsageBasedFeeCalculator implements FeeCalculator {
    private static final Logger log = LogManager.getLogger(UsageBasedFeeCalculator.class);

    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;
    private final List<QueryResourceUsageEstimator> queryUsageEstimators;
    private final PricedUsageCalculator pricedUsageCalculator;
    private final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;

    public UsageBasedFeeCalculator(
            final HbarCentExchange exchange,
            final AutoCreationLogic autoCreationLogic,
            final UsagePricesProvider usagePrices,
            final PricedUsageCalculator pricedUsageCalculator,
            final Set<QueryResourceUsageEstimator> queryUsageEstimators,
            final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.txnUsageEstimators = txnUsageEstimators;
        this.queryUsageEstimators = new ArrayList<>(queryUsageEstimators);
        this.pricedUsageCalculator = pricedUsageCalculator;

        autoCreationLogic.setFeeCalculator(this);
    }

    private FeeObject compute(
            Query query, FeeData usagePrices, Timestamp at, Function<QueryResourceUsageEstimator, FeeData> usageFn) {
        var usageEstimator = getQueryUsageEstimator(query);
        var queryUsage = usageFn.apply(usageEstimator);
        return computeFromQueryResourceUsage(queryUsage, usagePrices, at);
    }

    /**
     * Computes the fees for a query, given the query's resource usage, the current prices, and the estimated consensus
     * time.
     *
     * @param queryUsage  the resource usage of the query
     * @param usagePrices the current prices
     * @param at          the estimated consensus time
     * @return the fees for the query
     */
    public FeeObject computeFromQueryResourceUsage(
            final FeeData queryUsage, final FeeData usagePrices, final Timestamp at) {
        return getFeeObject(usagePrices, queryUsage, exchange.rate(at));
    }

    @Override
    public FeeObject estimatePayment(Query query, FeeData usagePrices, Store store, Timestamp at, ResponseType type) {
        return compute(query, usagePrices, at, estimator -> estimator.usageGivenType(query, store));
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = exchange.rate(at);
        var prices = usagePrices.defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    @Override
    public FeeObject computeFee(TxnAccessor accessor, JKey payerKey, Store store, Timestamp at) {
        return feeGiven(accessor, payerKey, store, usagePrices.activePrices(accessor), exchange.rate(at), true);
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    private FeeObject feeGiven(
            final TxnAccessor accessor,
            final JKey payerKey,
            final Store store,
            final Map<SubType, FeeData> prices,
            final ExchangeRate rate,
            final boolean inHandle) {
        final var function = accessor.getFunction();
        if (pricedUsageCalculator.supports(function)) {
            final var applicablePrices = prices.get(accessor.getSubType());
            return inHandle
                    ? pricedUsageCalculator.inHandleFees(applicablePrices, rate)
                    : pricedUsageCalculator.extraHandleFees(applicablePrices, rate);
        } else {
            var sigUsage = getSigUsage(accessor, payerKey);
            var usageEstimator = getTxnUsageEstimator(accessor);
            try {
                final var usage = usageEstimator.usageGiven(accessor.getTxn(), sigUsage, store);
                final var applicablePrices = prices.get(usage.getSubType());
                return getFeeObject(usage, applicablePrices, rate);
            } catch (Exception e) {
                log.warn(
                        "Argument accessor={} malformed for implied estimator {}!",
                        accessor.getSignedTxnWrapper(),
                        usageEstimator);
                throw new IllegalArgumentException(e);
            }
        }
    }

    private QueryResourceUsageEstimator getQueryUsageEstimator(Query query) {
        for (final QueryResourceUsageEstimator estimator : queryUsageEstimators) {
            if (estimator.applicableTo(query)) {
                return estimator;
            }
        }
        throw new NoSuchElementException("No estimator exists for the given query");
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

    public SigValueObj getSigUsage(TxnAccessor accessor, JKey payerKey) {
        int numPayerKeys = numSimpleKeys(payerKey);
        final var sigUsage = accessor.usageGiven(numPayerKeys);
        return new SigValueObj(sigUsage.numSigs(), numPayerKeys, sigUsage.sigsSize());
    }

    /**
     * Performs a left-to-right DFS of the Hedera key structure, offering each simple key to the provided
     * {@link Consumer}.
     *
     * @param key               the top-level Hedera key to traverse.
     * @param actionOnSimpleKey the logic to apply to each visited simple key.
     */
    public static void visitSimpleKeys(final JKey key, final Consumer<JKey> actionOnSimpleKey) {
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
}
