package com.hedera.mirror.web3.evm.util;

import com.google.common.primitives.Longs;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EntityUtils {
    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(
                bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18],
                bytes[19]);
    }
}
