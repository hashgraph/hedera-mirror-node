package com.hedera.mirror.test.e2e.acceptance.response;

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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import javax.inject.Named;
import lombok.Data;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;

@Data
@Named
public class ContractCallResponse {
    private String result;

    public static BigInteger convertContractCallResponseToNum(final ContractCallResponse response) {
        return Bytes.fromHexString(response.getResult()).toBigInteger();
    }

    public static String convertContractCallResponseToSelector(final ContractCallResponse response) {
        return Bytes.fromHexString(response.getResult()).trimTrailingZeros().toUnprefixedHexString();
    }

    public static String convertContractCallResponseToAddress(final ContractCallResponse response) {
        return Bytes.fromHexString(response.getResult()).slice(12).toUnprefixedHexString();
    }

    public String getResultAsText(String str) throws DecoderException {
        byte[] bytes = Hex.decodeHex(str.substring(2));
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public boolean convertToBoolean(final ContractCallResponse response) {
        return Long.parseUnsignedLong(response.getResult().replace("0x", ""), 16) > 0;
    }
}
