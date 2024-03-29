/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.convert;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final String ERROR_SIGNATURE = "0x08c379a0";
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");
    private static final int SIGNATURE_BYTES_LENGTH = 4;

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final Bytes revertReason) {
        boolean isNullOrEmpty = revertReason == null || revertReason.isEmpty();

        if (isNullOrEmpty || revertReason.size() <= SIGNATURE_BYTES_LENGTH) {
            return StringUtils.EMPTY;
        }

        if (revertReason.toHexString().startsWith(ERROR_SIGNATURE)) {
            final var encodedMessage = revertReason.slice(SIGNATURE_BYTES_LENGTH);
            final var tuple = STRING_DECODER.decode(encodedMessage.toArray());
            if (tuple.size() > 0) {
                return tuple.get(0);
            }
        }
        return StringUtils.EMPTY;
    }
}
