package com.hedera.mirror.web3.controller;

import com.google.protobuf.InvalidProtocolBufferException;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.fees.PricesAndFeesProvider;
import com.hedera.mirror.web3.evm.fees.RequiredPriceTypes;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

@Component
@RequiredArgsConstructor
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private static final Logger log = LogManager.getLogger(PricesAndFeesImpl.class);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);
    private ExchangeRateSet grpcRates = null;
    private final PricesAndFeesRepository pricesAndFeesRepository;
    CurrentAndNextFeeSchedule feeSchedules;
    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;
    private static final long DEFAULT_FEE = 100_000L;
    private static final int FEE_DIVISOR_FACTOR = 1000;
    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES = FeeComponents.newBuilder().setMin(DEFAULT_FEE)
            .setMax(DEFAULT_FEE).setConstant(0).setBpt(0).setVpt(0).setRbh(0).setSbh(0).setGas(0).setTv(0).setBpr(0)
            .setSbpr(0).build();
    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES = Map.of(DEFAULT, FeeData.newBuilder()
            .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES).setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
            .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES).build());

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        loadPriceSchedules(at.getSeconds());
        return pricesGiven(function, at).get(DEFAULT);
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        return rateAt(now.getSeconds());
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = rate(at);
        var prices = defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    private ExchangeRate rateAt(final long now) {
        loadRates(now);
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }

    public void loadRates(final long now) {
        final var ratesFile = pricesAndFeesRepository.getExchangeRate(now);

        try {
            this.grpcRates = ExchangeRateSet.parseFrom(ratesFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID.toString(), e);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    private void loadPriceSchedules(final long now) {
        byte[] feeScheduleFile = new byte[0];
        if (now > 0) {
            feeScheduleFile = pricesAndFeesRepository.getFeeSchedule(now);
        }

        try {
            final var schedules = CurrentAndNextFeeSchedule.parseFrom(feeScheduleFile);
            setFeeSchedules(schedules);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID.toString(),
                    e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }

    public void setFeeSchedules(final CurrentAndNextFeeSchedule feeSchedules) {
        this.feeSchedules = feeSchedules;

        currFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
        currFunctionUsagePricesExpiry = asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());

        nextFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
        nextFunctionUsagePricesExpiry = asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
    }

    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices = applicableUsagePrices(at);
            Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (Exception e) {
            log.debug("Default usage price will be used, no specific usage prices available for function {} @ {}!",
                    function, Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(final Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    EnumMap<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(final FeeSchedule feeSchedule) {
        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> allPrices = new EnumMap<>(HederaFunctionality.class);
        for (var pricingData : feeSchedule.getTransactionFeeScheduleList()) {
            final var function = pricingData.getHederaFunctionality();
            Map<SubType, FeeData> pricesMap = allPrices.get(function);
            if (pricesMap == null) {
                pricesMap = new EnumMap<>(SubType.class);
            }
            final Set<SubType> requiredTypes = RequiredPriceTypes.requiredTypesFor(function);
            ensurePricesMapHasRequiredTypes(pricingData, pricesMap, requiredTypes);
            allPrices.put(pricingData.getHederaFunctionality(), pricesMap);
        }
        return allPrices;
    }

    private Timestamp asTimestamp(final TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
    }

    void ensurePricesMapHasRequiredTypes(final TransactionFeeSchedule tfs, final Map<SubType, FeeData> pricesMap,
                                         final Set<SubType> requiredTypes) {
        /* The deprecated prices are the final fallback; if even they are not set, the function will be free */
        final var oldDefaultPrices = tfs.getFeeData();
        FeeData newDefaultPrices = null;
        for (var typedPrices : tfs.getFeesList()) {
            final var type = typedPrices.getSubType();
            if (requiredTypes.contains(type)) {
                pricesMap.put(type, typedPrices);
            }
            if (type == DEFAULT) {
                newDefaultPrices = typedPrices;
            }
        }
        for (var type : requiredTypes) {
            if (!pricesMap.containsKey(type)) {
                if (newDefaultPrices != null) {
                    pricesMap.put(type, newDefaultPrices.toBuilder().setSubType(type).build());
                } else {
                    pricesMap.put(type, oldDefaultPrices.toBuilder().setSubType(type).build());
                }
            }
        }
    }

    private boolean onlyNextScheduleApplies(final Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds() && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    private long currentPrice(final Instant now, final HederaFunctionality function,
                              final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = getTinybarsFromTinyCents(rate(timestamp), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);
        final var curMultiplier = 1L;

        return unscaledPrice * curMultiplier;
    }

    private long currentFeeInTinycents(final Instant now, final HederaFunctionality function,
                                       final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        final var prices = defaultPricesGiven(function, timestamp);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    private static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount).multiply(aMultiplier).divide(bDivisor).longValueExact();
    }
}

