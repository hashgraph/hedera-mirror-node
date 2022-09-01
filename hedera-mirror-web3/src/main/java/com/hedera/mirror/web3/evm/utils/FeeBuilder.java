package com.hedera.mirror.web3.evm.utils;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import java.math.BigInteger;

public class FeeBuilder {
    public static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount).multiply(aMultiplier).divide(bDivisor).longValueExact();
    }
}
