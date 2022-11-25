package com.hedera.mirror.web3.evm.contracts.execution;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;

@Named
@RequiredArgsConstructor
public class PricesAndFeesImpl implements PricesAndFeesProvider {
    private final PricesAndFeesRepository pricesAndFeesRepository;

    private static final Logger log = LogManager.getLogger(PricesAndFeesImpl.class);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = new EntityId(0L, 0L, 111L, EntityType.FILE);


    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
//        return currentPrice(now, function, FeeComponents::getGas);
        return 120000000L;
    }

//    public long currentPrice(
//            final Instant now,
//            final HederaFunctionality function,
//            final ToLongFunction<FeeComponents> resourcePriceFn) {
//        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
//        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
//        long feeInTinyBars = PricesAndFeesUtils.getTinybarsFromTinyCents(rate(timestamp), feeInTinyCents);
//        final var unscaledPrice = Math.max(1L, feeInTinyBars);
//        final var curMultiplier = 1L;
//        return unscaledPrice * curMultiplier;
//    }
//
//    private long currentFeeInTinycents(
//            final Instant now,
//            final HederaFunctionality function,
//            final ToLongFunction<FeeComponents> resourcePriceFn) {
//        final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
//        final var prices = defaultPricesGiven(function, timestamp);
//
//        /* Fee schedule prices are set in thousandths of a tinycent */
//        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
//    }
//
//    public void getExchangeRates(final long now) {
//        final var ratesFile = pricesAndFeesRepository.getExchangeRate(now);
//
//        try {
//            final var exchangeRates = ExchangeRateSet.parseFrom(ratesFile);
//        } catch (InvalidProtocolBufferException e) {
//            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID.toString(), e);
//            throw new IllegalStateException(String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
//        }
//    }

}

