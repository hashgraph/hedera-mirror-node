/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.response;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;

import jakarta.inject.Named;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;

@Data
@Named
public class ContractCallResponse {

    private String result;

    public BigInteger getResultAsNumber() {
        return getResultAsBytes().toBigInteger();
    }

    public String getResultAsSelector() {
        return getResultAsBytes().trimTrailingZeros().toUnprefixedHexString();
    }

    public String getResultAsAddress() {
        return getResultAsBytes().slice(12).toUnprefixedHexString();
    }

    public boolean getResultAsBoolean() {
        return Long.parseUnsignedLong(result.replace("0x", ""), 16) > 0;
    }

    public Bytes getResultAsBytes() {
        return Bytes.fromHexString(result);
    }

    public String getResultAsText() {
        var bytes = getResultAsBytes().toArrayUnsafe();
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public String getResultAsAsciiString() {
        // 1st 32 bytes - string info
        // 2nd 32 bytes - data length in the last 32 bytes
        // 3rd 32 bytes - actual string suffixed with zeroes
        return hexToAscii(result.replace("0x", "").substring(128).trim());
    }
}
