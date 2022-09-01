package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import lombok.RequiredArgsConstructor;
import javax.inject.Named;

@Named
@RequiredArgsConstructor
public final class BasicHbarCentExchange implements HbarCentExchange {

    private ExchangeRateSet grpcRates;
    @Override
    public ExchangeRate rate(Timestamp now) {
        return rateAt(now.getSeconds());
    }

    private ExchangeRate rateAt(final long now) {
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }
}
