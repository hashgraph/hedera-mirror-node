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

package com.hedera.mirror.test.e2e.acceptance.util;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;

import com.google.common.base.Splitter;
import com.hedera.mirror.rest.model.ContractCallResponse;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

/**
 * Wraps an instance of OpenAPI model object {@link ContractCallResponse} and provides additional convenience
 * functionality that was present in the POJOs used previously.
 */
@RequiredArgsConstructor(staticName = "of")
public class ContractCallResponseWrapper {
    @NonNull private final ContractCallResponse response;

    public String getResult() {
        return response.getResult();
    }

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
        return Long.parseUnsignedLong(response.getResult().replace("0x", ""), 16) > 0;
    }

    public Bytes getResultAsBytes() {
        return Bytes.fromHexString(response.getResult());
    }

    public String getResultAsText() {
        var bytes = getResultAsBytes().toArrayUnsafe();
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public String getResultAsAsciiString() {
        // 1st 32 bytes - string info
        // 2nd 32 bytes - data length in the last 32 bytes
        // 3rd 32 bytes - actual string suffixed with zeroes
        return hexToAscii(response.getResult().replace("0x", "").substring(128).trim());
    }

    public List<BigInteger> getResultAsListDecimal() {
        var result = response.getResult().replace("0x", "");

        return Splitter.fixedLength(64)
                .splitToStream(result)
                .map(TestUtil::hexToDecimal)
                .toList();
    }

    public List<String> getResultAsListAddress() {
        var result = response.getResult().replace("0x", "");

        return Splitter.fixedLength(64).splitToStream(result).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContractCallResponseWrapper contractCallResponseWrapper = (ContractCallResponseWrapper) o;
        return Objects.equals(this.response, contractCallResponseWrapper.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response);
    }
}
