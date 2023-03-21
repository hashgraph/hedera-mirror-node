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
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;

@Data
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

    public boolean getResultAsBoolean() {
        return Long.parseUnsignedLong(result.replace("0x", ""), 16) > 0;
    }

    public String getResultAsAsciiString() {
        // 1st 32 bytes - string info
        // 2nd 32 bytes - data length in the last 32 bytes
        // 3rd 32 bytes - actual string suffixed with zeroes
        return hexToAscii(result.replace("0x", "").substring(128).trim());
    }
}
