package com.hedera.mirror.web3.evm.utils;

import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@UtilityClass
public class BlockHashUtil {

    public static org.hyperledger.besu.datatypes.Hash ethHashFrom(final String hash) {
        final byte[] hashBytesToConvert = Bytes.fromHexString(hash).toArrayUnsafe();
        final byte[] prefixBytes = new byte[32];
        System.arraycopy(hashBytesToConvert, 0, prefixBytes, 0, 32);
        return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(prefixBytes));
    }
}
