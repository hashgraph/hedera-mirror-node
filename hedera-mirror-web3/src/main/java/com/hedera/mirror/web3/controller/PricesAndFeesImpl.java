package com.hedera.mirror.web3.controller;

import com.google.protobuf.InvalidProtocolBufferException;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;

import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.contracts.execution.PricesAndFeesUtils;

import com.hedera.services.evm.contracts.loader.impl.PricesAndFeesLoaderImpl;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import javax.inject.Inject;
import java.time.Instant;
import java.util.function.ToLongFunction;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

@Component
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private final PricesAndFeesUtils utils;
    private final PricesAndFeesRepository pricesAndFeesRepository;
    private final PricesAndFeesLoaderImpl loader;

    @Inject
    public PricesAndFeesImpl(PricesAndFeesUtils utils, PricesAndFeesRepository pricesAndFeesRepository,
                             PricesAndFeesLoaderImpl loader) {
        this.utils = utils;
        this.pricesAndFeesRepository = pricesAndFeesRepository;
        this.loader = loader;
    }

    private static final Logger log = LogManager.getLogger(PricesAndFeesImpl.class);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        getFeeSchedules(at.getSeconds());
        return utils.pricesGiven(function, at).get(DEFAULT);
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        getExchangeRates(now.getSeconds());
        return utils.rateAt(now.getSeconds());
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    public long currentPrice(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = PricesAndFeesUtils.getTinybarsFromTinyCents(rate(timestamp), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);
        final var curMultiplier = 1L;
        return unscaledPrice * curMultiplier;
    }

    private long currentFeeInTinycents(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        final var prices = defaultPricesGiven(function, timestamp);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }

    public void getExchangeRates(final long now) {
        final var ratesFile = pricesAndFeesRepository.getExchangeRate(now);

        try {
            final var exchangeRates = ExchangeRateSet.parseFrom(ratesFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID.toString(), e);
            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    public void getFeeSchedules(final long now) {
        byte[] feeScheduleFile = new byte[0];
        if (now > 0) {
            feeScheduleFile = pricesAndFeesRepository.getFeeSchedule(now);
        }

        try {
            final var schedules = CurrentAndNextFeeSchedule.parseFrom(feeScheduleFile);
            loader.getFeeSchedules(now);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt fee schedules file at {}, may require remediation!", FEE_SCHEDULE_ENTITY_ID.toString(),
                    e);
            throw new IllegalStateException(String.format("Fee schedule %s is corrupt!", FEE_SCHEDULE_ENTITY_ID));
        }
    }
}

