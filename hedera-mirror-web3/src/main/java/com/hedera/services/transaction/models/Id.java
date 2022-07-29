package com.hedera.services.transaction.models;

import static java.lang.System.arraycopy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.swirlds.common.utility.CommonUtils;
import org.hyperledger.besu.datatypes.Address;

/**
 * Represents the id of a Hedera entity (account, topic, token, contract, file, or schedule).
 */
public record Id(long shard, long realm, long num) {
    /**
     * Returns the EVM representation of the Account
     *
     * @return {@link Address} evm representation
     */
    public Address asEvmAddress() {
        return Address.fromHexString(asHexedEvmAddress(this));
    }

    public String asHexedEvmAddress(final Id id) {
        return CommonUtils.hex(asEvmAddress((int) id.shard(), id.realm(), id.num()));
    }

    private byte[] asEvmAddress(final int shard, final long realm, final long num) {
        final byte[] evmAddress = new byte[20];

        arraycopy(Ints.toByteArray(shard), 0, evmAddress, 0, 4);
        arraycopy(Longs.toByteArray(realm), 0, evmAddress, 4, 8);
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);

        return evmAddress;
    }
}
