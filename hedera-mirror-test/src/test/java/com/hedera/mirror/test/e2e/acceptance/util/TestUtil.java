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

package com.hedera.mirror.test.e2e.acceptance.util;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.Key;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@UtilityClass
public class TestUtil {
    private static final BaseEncoding BASE32_ENCODER = BaseEncoding.base32().omitPadding();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
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
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static Address asAddress(String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(ExpandedAccountId accountId) {
        final var address = accountId.getAccountId().toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(TokenId tokenId) {
        final var address = tokenId.toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(ContractId contractId) {
        final var address = contractId.toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(AccountId accountId) {
        final var address = accountId.toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(ContractClient contractClient) {
        final var address = contractClient.getClientAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(TokenClient tokenClient) {
        final var address = tokenClient
                .getSdkClient()
                .getExpandedOperatorAccountId()
                .getAccountId()
                .toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Address asAddress(byte[] bytes) {
        final var addressBytes = Bytes.wrap(bytes);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }

    public static Tuple accountAmount(String account, Long amount, boolean isApproval) {
        return Tuple.of(asAddress(account), amount, isApproval);
    }

    public static Tuple nftAmount(String sender, String receiver, Long serialNumber, boolean isApproval) {
        return Tuple.of(asAddress(sender), asAddress(receiver), serialNumber, isApproval);
    }

    public static Address[] asAddressArray(List<String> addressStrings) {
        return addressStrings.stream().map(addr -> asAddress(addr)).toArray(Address[]::new);
    }

    public static byte[][] asByteArray(List<String> hexStringList) {
        return hexStringList.stream()
                .map(hexString -> Bytes.fromHexString(hexString).toArrayUnsafe())
                .toArray(byte[][]::new);
    }

    public static long[] asLongArray(final List<Long> longList) {
        return longList.stream().mapToLong(Long::longValue).toArray();
    }

    public static String getAbiFunctionAsJsonString(CompiledSolidityArtifact artifact, String functionName) {
        return Arrays.stream(artifact.getAbi())
                .filter(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return Objects.equals(functionName, map.get("name"));
                    }
                    return false;
                })
                .map(TestUtil::toJson)
                .findFirst()
                .orElseThrow();
    }

    @SneakyThrows
    private static String toJson(Object object) {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static BigInteger hexToDecimal(String hex) {
        return Bytes32.fromHexString(hex).toBigInteger();
    }

    public static byte[] nextBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static class TokenTransferListBuilder {
        private Tuple tokenTransferList;
        private Address token;

        public TokenTransferListBuilder forToken(final String token) {
            this.token = asAddress(token);
            return this;
        }

        public TokenTransferListBuilder forTokenAddress(final Address token) {
            this.token = token;
            return this;
        }

        public TokenTransferListBuilder withAccountAmounts(final Tuple... accountAmounts) {
            this.tokenTransferList = Tuple.of(token, accountAmounts, new Tuple[] {});
            return this;
        }

        public TokenTransferListBuilder withNftTransfers(final Tuple... nftTransfers) {
            this.tokenTransferList = Tuple.of(token, new Tuple[] {}, nftTransfers);
            return this;
        }

        public Tuple build() {
            return tokenTransferList;
        }
    }
}
