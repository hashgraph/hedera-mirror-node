package com.hedera.mirror.common.util;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ByteString;
import com.google.protobuf.Internal;
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.services.stream.proto.HashObject;

class DomainUtilsTest {

    private static final String ED25519_KEY = "60beee88a761e079f71b03b5fe041979369e96450a7455b203a2578c8a7d4852";
    private static final String ECDSA_384_KEY = "0000001365636473612d736861322d6e69737470333834000000086e697374703338" +
            "340000006104414e37848e6bbf0824d32e434c315eac3c23300db659f6058ed160ff8f6e80e2353d280b95c876b9f97b785f0a0f" +
            "c8f76842241c2484966791344fd54bc103ce57380052abade51b5b4cf21fad404fe6a1ee7228bff3cd2fbfd617802d8f625b";
    private static final String ECDSA_SECP256K1_KEY = "0347ba1b98360d856fb796ecbcbde934055b9e0967e5e40d0abeb3fd68bf83" +
            "82c2";
    private static final String RSA_3072_KEY = "308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c34" +
            "471f9db1efd5345cb664ed7bb980a8a859f41a51c4a009a91a1db51401af9dea56ce00ca09050c0d02f49ee58b68b24760f077c4" +
            "079f4345ea2e78f4e8f52518a437dc3db47b611ce0574b56d064899892b7378d65491e65676cdf3f2f2b07f34f8bec515f3d4037" +
            "6b3a0a13615c8fc8348091091bb8dcd3a617de7b1bd5958c66ca108632e208955a6d2b6bc3421175cc2a2a67a5cafd8562dc7360" +
            "0006bb44b4796dceb7430c0c62cb0ae1618e391c1284e8e750a40991aa4c4dee6e450c26d2347b9e20865f9cad3de21033ad9aa3" +
            "eee092b9fc3df3fc35533b16530204bc171ef7573138fd212bd559fe2071295542cb40090415bf397bbf474adb6bacba32fe13c0" +
            "817a8f202e659588c5608a12fc2707795b3b621434305b7d4f6d6271ebe62a7afd98c2f662f4ed5f8c4bd0ee603b896e53e2ca2d" +
            "ed7495515c1e88a40f58fd6f94f8d9f14613470ba873d395293ea5542ddea56550f44d5760f57394693a889a43ab6f73b3a55448" +
            "8ecadacc328cb02594a2a5e9e46602010e2430203010001";

    private static Stream<Arguments> paddingByteProvider() {
        return Stream.of(
                arguments(new byte[] {1, 3, 4}, 0, new byte[] {1, 3, 4}),
                arguments(new byte[] {1, 3, 4}, 1, new byte[] {1, 3, 4}),
                arguments(new byte[] {1, 3, 4}, 3, new byte[] {1, 3, 4}),
                arguments(new byte[] {1, 3, 4}, 5, new byte[] {0, 0, 1, 3, 4}),
                arguments(new byte[] {}, 3, new byte[] {0, 0, 0}),
                arguments(new byte[] {1}, 5, new byte[] {0, 0, 0, 0, 1}),
                arguments(new byte[] {1, 2}, -2, new byte[] {1, 2}),
                arguments(null, 15, new byte[] {})
        );
    }

    @Test
    void getPublicKeyWhenNull() {
        assertThat(DomainUtils.getPublicKey(null)).isNull();
    }

    @Test
    void getPublicKeyWhenError() {
        assertThat(DomainUtils.getPublicKey(new byte[] {0, 1, 2})).isNull();
    }

    @Test
    void getPublicKeyWhenEd25519() throws Exception {
        var bytes = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519_KEY))).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(ED25519_KEY);
    }

    @Test
    void getPublicKeyWhenECDSASecp256K1() throws Exception {
        var bytes = Key.newBuilder().setECDSASecp256K1(
                ByteString.copyFrom(Hex.decodeHex(ECDSA_SECP256K1_KEY))).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(ECDSA_SECP256K1_KEY);
    }

    @Test
    void getPublicKeyWhenECDSA384() throws Exception {
        var bytes = Key.newBuilder().setECDSA384(
                ByteString.copyFrom(Hex.decodeHex(ECDSA_384_KEY))).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(ECDSA_384_KEY);
    }

    @Test
    void getPublicKeyWhenRSA3072() throws Exception {
        var bytes = Key.newBuilder().setRSA3072(ByteString.copyFrom(Hex.decodeHex(RSA_3072_KEY))).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(RSA_3072_KEY);
    }

    @Test
    void getPublicKeyWhenDefaultInstance() {
        byte[] keyBytes = Key.getDefaultInstance().toByteArray();
        assertThat(DomainUtils.getPublicKey(keyBytes)).isEmpty();
    }

    @Test
    void getPublicKeyWhenEmpty() {
        byte[] keyBytes = Key.newBuilder().setEd25519(ByteString.EMPTY).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(keyBytes)).isEmpty();
    }

    @Test
    void getPublicKeyWhenSimpleKeyList() throws Exception {
        var key = Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(Hex.decodeHex(ECDSA_SECP256K1_KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(ECDSA_SECP256K1_KEY);
    }

    @Test
    void getPublicKeyWhenMaxDepth() throws Exception {
        var primitiveKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519_KEY))).build();
        var keyList2 = KeyList.newBuilder().addKeys(primitiveKey).build();
        var key2 = Key.newBuilder().setKeyList(keyList2).build();
        var keyList1 = KeyList.newBuilder().addKeys(key2).build();
        var bytes = Key.newBuilder().setKeyList(keyList1).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isNull();
    }

    @Test
    void getPublicKeyWhenKeyList() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519_KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isNull();
    }

    @Test
    void getPublicKeyWhenSimpleThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519_KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isEqualTo(ED25519_KEY);
    }

    @Test
    void getPublicKeyWhenThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519_KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(DomainUtils.getPublicKey(bytes)).isNull();
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void convertInstantToNanos(long seconds, int nanos) {
        Long timeNanos = DomainUtils.convertToNanos(seconds, nanos);
        Instant fromTimeStamp = Instant.ofEpochSecond(0, timeNanos);

        assertAll(
                () -> assertEquals(seconds, fromTimeStamp.getEpochSecond())
                , () -> assertEquals(nanos, fromTimeStamp.getNano())
        );
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1568376750538, 0",
            "9223372036854775807, 0",
            "-9223372036854775808, 0",
            "9223372036, 854775808",
            "-9223372036, -854775809"
    })
    void convertInstantToNanosThrows(long seconds, int nanos) {
        assertThrows(ArithmeticException.class, () -> DomainUtils.convertToNanos(seconds, nanos));
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "0, 0, 0",
            "1574880387, 0, 1574880387000000000",
            "1574880387, 999999999, 1574880387999999999",
            "1568376750538, 0, 9223372036854775807",
            "9223372036854775807, 0, 9223372036854775807",
            "-9223372036854775808, 0, -9223372036854775808",
            "9223372036, 854775808, 9223372036854775807",
            "-9223372036, -854775809, -9223372036854775808"
    })
    void convertInstantToNanosMax(long seconds, int nanos, long expected) {
        assertThat(DomainUtils.convertToNanosMax(seconds, nanos)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "with instant {0}")
    @CsvSource({
            ", 0",
            "2019-11-27T18:46:27Z, 1574880387000000000",
            "2019-11-27T18:46:27.999999999Z, 1574880387999999999",
            "2262-04-11T23:47:16.854775807Z, 9223372036854775807",
            "2262-04-11T23:47:16.854775808Z, 9223372036854775807",
    })
    void convertInstantToNanosMax(Instant instant, long expected) {
        assertThat(DomainUtils.convertToNanosMax(instant)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "leftPadBytes: ({0}, {1}): {2}")
    @MethodSource("paddingByteProvider")
    void leftPadBytes(byte[] bytes, int paddingLength, byte[] expected) {
        assertThat(DomainUtils.leftPadBytes(bytes, paddingLength)).isEqualTo(expected);
    }

    @Test
    void sanitize() {
        assertThat(DomainUtils.sanitize(null)).isNull();
        assertThat(DomainUtils.sanitize("")).isEmpty();
        assertThat(DomainUtils.sanitize("abc")).isEqualTo("abc");
        assertThat(DomainUtils.sanitize("abc" + (char) 0 + "123" + (char) 0).getBytes(StandardCharsets.UTF_8))
                .isEqualTo("abc�123�".getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void timeStampInNanosTimeStamp(long seconds, int nanos) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();

        Long timeStampInNanos = DomainUtils.timeStampInNanos(timestamp);
        assertThat(timeStampInNanos).isNotNull();
        Instant fromTimeStamp = Instant.ofEpochSecond(0, timeStampInNanos);

        assertAll(
                () -> assertEquals(timestamp.getSeconds(), fromTimeStamp.getEpochSecond())
                , () -> assertEquals(timestamp.getNanos(), fromTimeStamp.getNano())
        );
    }

    @Test
    @DisplayName("converting illegal timestamp to nanos")
    void timeStampInNanosInvalid() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1568376750538L).setNanos(0).build();
        assertThrows(ArithmeticException.class, () -> {
            DomainUtils.timeStampInNanos(timestamp);
        });
    }

    @Test
    void toBytes() {
        byte[] smallArray = RandomUtils.nextBytes(DomainUtils.UnsafeByteOutput.SIZE);
        byte[] largeArray = RandomUtils.nextBytes(256);

        assertThat(DomainUtils.toBytes(null)).isNull();
        assertThat(DomainUtils.toBytes(ByteString.EMPTY)).isEqualTo(new byte[0]).isSameAs(Internal.EMPTY_BYTE_ARRAY);
        assertThat(DomainUtils.toBytes(UnsafeByteOperations.unsafeWrap(smallArray)))
                .isEqualTo(smallArray)
                .isNotSameAs(smallArray);

        assertThat(DomainUtils.toBytes(UnsafeByteOperations.unsafeWrap(largeArray)))
                .isEqualTo(largeArray)
                .isSameAs(largeArray);

        assertThat(DomainUtils.toBytes(UnsafeByteOperations.unsafeWrap(ByteBuffer.wrap(largeArray))))
                .isEqualTo(largeArray)
                .isNotSameAs(largeArray);
    }

    @Test
    void fromBytes() {
        byte[] bytes = RandomUtils.nextBytes(16);

        assertThat(DomainUtils.fromBytes(null)).isNull();
        assertThat(DomainUtils.fromBytes(new byte[0])).isEqualTo(ByteString.EMPTY);
        assertThat(DomainUtils.fromBytes(bytes).toByteArray()).isEqualTo(bytes);
    }

    @Test
    void fromEvmAddress() {
        long shard = 1;
        long realm = 2;
        long num = 255;
        byte[] evmAddress = new byte[20];
        evmAddress[3] = (byte) shard;
        evmAddress[11] = (byte) realm;
        evmAddress[19] = (byte) num;
        EntityId expected = EntityId.of(shard, realm, num, EntityType.CONTRACT);
        assertThat(DomainUtils.fromEvmAddress(evmAddress)).isEqualTo(expected);

        evmAddress[0] = (byte) 255;
        evmAddress[4] = (byte) 255;
        evmAddress[12] = (byte) 255;
        // can't be encoded to long
        assertThat(DomainUtils.fromEvmAddress(evmAddress)).isNull();
    }

    @Test
    void fromEvmAddressIncorrectSize() {
        assertNull(DomainUtils.fromEvmAddress(null));
        assertNull(DomainUtils.fromEvmAddress(new byte[10]));
    }

    @Test
    void toEvmAddressEntityId() {
        EntityId contractId = EntityId.of(1, 2, 255, EntityType.CONTRACT);
        String expected = "00000001000000000000000200000000000000FF";
        assertThat(DomainUtils.toEvmAddress(contractId)).asHexString().isEqualTo(expected);
        assertThrows(InvalidEntityException.class, () -> DomainUtils.toEvmAddress((EntityId) null));
        assertThrows(InvalidEntityException.class, () -> DomainUtils.toEvmAddress(EntityId.EMPTY));
    }

    @Test
    void toEvmAddressContractID() throws Exception {
        String expected = "00000001000000000000000200000000000000FF";
        ContractID contractId = ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(255).build();
        ContractID contractIdEvm = ContractID.newBuilder()
                .setEvmAddress(DomainUtils.fromBytes(Hex.decodeHex(expected))).build();
        ContractID contractIdDefault = ContractID.getDefaultInstance();
        assertThat(DomainUtils.toEvmAddress(contractId)).asHexString().isEqualTo(expected);
        assertThat(DomainUtils.toEvmAddress(contractIdEvm)).asHexString().isEqualTo(expected);
        assertThrows(InvalidEntityException.class, () -> DomainUtils.toEvmAddress((ContractID) null));
        assertThrows(InvalidEntityException.class, () -> DomainUtils.toEvmAddress(contractIdDefault));
    }

    @Test
    void bytesToHex() {
        assertThat(DomainUtils.bytesToHex(new byte[] {1})).isEqualTo("01");
        assertThat(DomainUtils.bytesToHex(new byte[] {127})).isEqualTo("7f");
        assertThat(DomainUtils.bytesToHex(new byte[] {-1})).isEqualTo("ff");
        assertThat(DomainUtils.bytesToHex(new byte[] {0})).isEqualTo("00");
        assertThat(DomainUtils.bytesToHex(new byte[] {00})).isEqualTo("00");
        assertThat(DomainUtils.bytesToHex(new byte[0])).isEmpty();
        assertThat(DomainUtils.bytesToHex(null)).isNull();
    }

    @Test
    void getHashBytes() {
        assertThat(DomainUtils.getHashBytes(HashObject.newBuilder().build())).isEqualTo(new byte[] {});
        assertThat(DomainUtils.getHashBytes(HashObject.newBuilder().setHash(ByteString.EMPTY).build()))
                .isEqualTo(new byte[] {});
        assertThat(DomainUtils.getHashBytes(HashObject.newBuilder().setHash(ByteString.fromHex("aabb0102")).build()))
                .isEqualTo(new byte[] {(byte) 0xaa, (byte) 0xbb, 1, 2});
    }
}
