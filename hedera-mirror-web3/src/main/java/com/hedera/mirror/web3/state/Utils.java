/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.Arrays;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.util.unit.DataSize;

@CustomLog
@UtilityClass
public class Utils {

    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;
    public static final int EVM_ADDRESS_LEN = 20;
    /* A placeholder to store the 12-byte of zeros prefix that marks an EVM address as a "mirror" address. */
    private static final byte[] MIRROR_PREFIX = new byte[12];
    // The configured data in the request is currently 128 KB. In services, we have a property for the
    // max signed transaction size. We put 1 KB more here to have a buffer because the transaction has other
    // fields (apart from the data) that will increase the transaction size.
    public static final long MAX_SIGNED_TXN_SIZE = DataSize.ofKilobytes(129).toBytes();

    public static Key parseKey(final byte[] keyBytes) {
        try {
            if (keyBytes != null && keyBytes.length > 0) {
                return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
            }
        } catch (final ParseException e) {
            return null;
        }

        return null;
    }

    /**
     * Converts a timestamp in nanoseconds to a PBJ Timestamp object.
     *
     * @param timestamp The timestamp in nanoseconds.
     * @return The PBJ Timestamp object.
     */
    public static Timestamp convertToTimestamp(final long timestamp) {
        var instant = Instant.ofEpochSecond(0, timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }

    public boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    public static boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }

        return Arrays.equals(MIRROR_PREFIX, 0, 12, address, 0, 12);
    }
}
