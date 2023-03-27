package com.hedera.mirror.web3.convert;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
public class BytesDecoder {

    //Error(string)
    private static final String ERROR_SIGNATURE = "0x08c379a0";
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");
    private static final int SIGNATURE_BYTES_LENGTH = 4;

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final Bytes revertReason) {
        if(revertReason == null || revertReason.isEmpty() || !revertReason.toHexString().startsWith(ERROR_SIGNATURE)
                || revertReason.size() <= SIGNATURE_BYTES_LENGTH) {
            return StringUtils.EMPTY;
        }

        final var encodedMessage = revertReason.slice(SIGNATURE_BYTES_LENGTH);
        return STRING_DECODER.decode(encodedMessage.toArray()).get(0);
    }
}
