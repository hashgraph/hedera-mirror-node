/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.proto.Key;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class TestUtil {
    private static final BaseEncoding BASE32_ENCODER = BaseEncoding.base32().omitPadding();
    public static String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    public static String getAliasFromPublicKey(@NonNull PublicKey key) {
        if (key.isECDSA()) {
            return BASE32_ENCODER.encode(Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFrom(key.toBytesRaw()))
                    .build()
                    .toByteArray());
        } else if (key.isED25519()) {
            return BASE32_ENCODER.encode(Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(key.toBytesRaw()))
                    .build()
                    .toByteArray());
        }

        throw new IllegalStateException("Unsupported key type");
    }

    public static String to32BytesString(String data) {
        return StringUtils.leftPad(data.replace("0x", ""), 64, '0');
    }

    public static String to32BytesStringRightPad(String data) {
        return StringUtils.rightPad(data.replace("0x", ""), 64, '0');
    }

    public static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }
}
