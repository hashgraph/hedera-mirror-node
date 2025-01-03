/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final Bytes ERROR_FUNCTION_SELECTOR = Bytes.fromHexString("0x08c379a0");
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final Bytes revertReason) {
        boolean isNullOrEmpty = revertReason == null || revertReason.isEmpty();

        if (isNullOrEmpty || revertReason.size() <= ERROR_FUNCTION_SELECTOR.size()) {
            return StringUtils.EMPTY;
        }

        if (isAbiEncodedErrorString(revertReason)) {
            final var encodedMessage = revertReason.slice(ERROR_FUNCTION_SELECTOR.size());
            final var tuple = STRING_DECODER.decode(encodedMessage.toArray());
            if (!tuple.isEmpty()) {
                return tuple.get(0);
            }
        }
        return StringUtils.EMPTY;
    }

    public static Bytes getAbiEncodedRevertReason(final String revertReason) {
        if (revertReason == null || revertReason.isEmpty()) {
            return Bytes.EMPTY;
        }
        if (revertReason.startsWith(HEX_PREFIX)) {
            return getAbiEncodedRevertReason(Bytes.fromHexString(revertReason));
        }
        return getAbiEncodedRevertReason(Bytes.of(revertReason.getBytes()));
    }

    public static Bytes getAbiEncodedRevertReason(final Bytes revertReason) {
        if (revertReason == null || revertReason.isEmpty()) {
            return Bytes.EMPTY;
        }
        if (isAbiEncodedErrorString(revertReason)) {
            return revertReason;
        }
        String revertReasonPlain = new String(revertReason.toArray());
        return Bytes.concatenate(
                ERROR_FUNCTION_SELECTOR, Bytes.wrapByteBuffer(STRING_DECODER.encode(Tuple.of(revertReasonPlain))));
    }

    private static boolean isAbiEncodedErrorString(final Bytes revertReason) {
        return revertReason != null
                && revertReason.commonPrefixLength(ERROR_FUNCTION_SELECTOR) == ERROR_FUNCTION_SELECTOR.size();
    }
}
