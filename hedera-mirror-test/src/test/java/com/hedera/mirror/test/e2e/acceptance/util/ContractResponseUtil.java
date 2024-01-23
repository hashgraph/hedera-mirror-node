package com.hedera.mirror.test.e2e.acceptance.util;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;

import com.google.common.base.Splitter;
import com.hedera.mirror.rest.model.ContractCallResponse;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

@RequiredArgsConstructor(staticName = "of")
public class ContractResponseUtil {
    @NonNull private ContractCallResponse response;

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
}
