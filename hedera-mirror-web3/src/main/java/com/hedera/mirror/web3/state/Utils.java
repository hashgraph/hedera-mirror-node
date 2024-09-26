/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;

@CustomLog
@UtilityClass
public class Utils {

    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;

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
     * Converts a timestamp in milliseconds to a PBJ Timestamp object.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return The PBJ Timestamp object.
     */
    public static Timestamp convertToTimestamp(final long timestamp) {
        var instant = Instant.ofEpochMilli(timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }

    public static Address convertPbjBytesToBesuAddress(final Bytes bytes) {
        final var evmAddressBytes = bytes.toByteArray();
        return Address.wrap(org.apache.tuweni.bytes.Bytes.wrap(evmAddressBytes));
    }
}
