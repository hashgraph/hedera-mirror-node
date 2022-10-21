package com.hedera.mirror.web3.controller;

import com.google.protobuf.InvalidProtocolBufferException;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.fees.PricesAndFeesProvider;
import com.hedera.mirror.web3.repository.PricesAndFeesRepository;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Component
@RequiredArgsConstructor
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private static final Logger log = LogManager.getLogger(PricesAndFeesImpl.class);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = new EntityId(0L, 0L, 112L, EntityType.FILE);
    private ExchangeRateSet grpcRates = null;
    private final PricesAndFeesRepository pricesAndFeesRepository;

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return null;
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        return rateAt(now.getSeconds());
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        return 0;
    }

    public void loadRates(final long now) {
        final var ratesFile = pricesAndFeesRepository.getExchangeRate(now);

        try {
            this.grpcRates = ExchangeRateSet.parseFrom(ratesFile);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Corrupt rate file at {}, may require remediation!", EXCHANGE_RATE_ENTITY_ID.toString(), e);
            throw new IllegalStateException(
                    String.format("Rates %s are corrupt!", EXCHANGE_RATE_ENTITY_ID));
        }
    }

    private ExchangeRate rateAt(final long now) {
        loadRates(now);
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }
}
