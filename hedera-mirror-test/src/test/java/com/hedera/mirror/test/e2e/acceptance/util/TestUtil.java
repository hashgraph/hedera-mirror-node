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

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.proto.Key;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import com.esaulpaugh.headlong.abi.Tuple;

import java.util.List;


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

    public static Address asHeadlongAddress(final String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Tuple accountAmount(final String account, final Long amount, final boolean isApproval) {
        return Tuple.of(asHeadlongAddress(account), amount, isApproval);
    }

    public static Address[] asHeadlongAddressArray(final List<String> addressStrings) {
        return addressStrings.stream()
                .map(addr -> asHeadlongAddress(addr))
                .toArray(Address[]::new);
    }

    public static long[] asLongArray(final List<Long> longList) {
        return longList.stream()
                .mapToLong(Long::longValue)
                .toArray();
    }

    public static class TokenTransferListBuilder {
        private Tuple tokenTransferList;
        private Address token;

        public TokenTransferListBuilder forToken(final String token) {
            this.token = asHeadlongAddress(token);
            return this;
        }

        public TokenTransferListBuilder forTokenAddress(final Address token) {
            this.token = token;
            return this;
        }

        public TokenTransferListBuilder withAccountAmounts(final Tuple... accountAmounts) {
            this.tokenTransferList = Tuple.of(token, accountAmounts, new Tuple[]{});
            return this;
        }

        public TokenTransferListBuilder withNftTransfers(final Tuple... nftTransfers) {
            this.tokenTransferList = Tuple.of(token, new Tuple[]{}, nftTransfers);
            return this;
        }

        public Tuple build() {
            return tokenTransferList;
        }
    }
}
