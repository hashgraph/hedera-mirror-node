package com.hedera.mirror.web3.evm.util;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;

@UtilityClass
public class AccountUtil {
    public static AccountID accountIdFromEvmAddress(final Address address) {
        final byte[] bytes = address.toArrayUnsafe();
        return AccountID.newBuilder()
                .setShardNum(shardFromEvmAddress(bytes))
                .setRealmNum(realmFromEvmAddress(bytes))
                .setAccountNum(numFromEvmAddress(bytes))
                .build();
    }

    public static long shardFromEvmAddress(final byte[] bytes) {
        return Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
    }

    public static long realmFromEvmAddress(final byte[] bytes) {
        return Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12));
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(
                bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18],
                bytes[19]);
    }
}
