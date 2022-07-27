package com.hedera.hederahashgraph.fee;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import java.math.BigInteger;

public class FeeBuilder {

    /**
     * Convert tinyCents to tinybars
     *
     * @param exchangeRate exchange rate
     * @param tinyCentsFee tiny cents fee
     * @return tinyHbars
     */
    public static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount).multiply(aMultiplier).divide(bDivisor).longValueExact();
    }

}
