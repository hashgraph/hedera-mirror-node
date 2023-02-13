package com.hedera.mirror.importer.util;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.ParserException;

@SuppressWarnings("java:S5786")
public class UtilityTest {

    public static final byte[] ALIAS_ECDSA_SECP256K1 = Hex.decode(
            "3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    public static final byte[] EVM_ADDRESS = Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    @Test
    void aliasToEvmAddress() {
        byte[] aliasEd25519 = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("ab")).build().toByteArray();
        byte[] aliasEcdsa2 = Hex.decode("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
        byte[] aliasInvalidEcdsa = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(TestUtils.generateRandomByteArray(33)))
                .build()
                .toByteArray();
        byte[] aliasUncompressedEcdsa = Hex.decode(
                "3a374349514637575a4f55475132415336334d504757573633484750484b584b5442344f3643505a334b3437374133354b584257525a4e4941");
        byte[] evmAddress2 = Hex.decode("efa0d905af20199aa03aca71cfa5f7647f29f439");
        byte[] randomBytes = TestUtils.generateRandomByteArray(DomainUtils.EVM_ADDRESS_LENGTH);
        byte[] tooShortBytes = TestUtils.generateRandomByteArray(32);
        byte[] invalidBytes = Bytes.concat(new byte[] {'a'}, TestUtils.generateRandomByteArray(32));

        assertThat(Utility.aliasToEvmAddress(ALIAS_ECDSA_SECP256K1)).isEqualTo(EVM_ADDRESS);
        assertThat(Utility.aliasToEvmAddress(aliasEcdsa2)).isEqualTo(evmAddress2);
        assertThat(Utility.aliasToEvmAddress(randomBytes)).isEqualTo(randomBytes);
        assertThat(Utility.aliasToEvmAddress(aliasInvalidEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasUncompressedEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasEd25519)).isNull();
        assertThat(Utility.aliasToEvmAddress(null)).isNull();
        assertThat(Utility.aliasToEvmAddress(new byte[] {})).isNull();
        assertThat(Utility.aliasToEvmAddress(tooShortBytes)).isNull();
        assertThatThrownBy(() -> Utility.aliasToEvmAddress(invalidBytes))
                .isInstanceOf(ParserException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"0,0", "86400000000000,1", "1653487416000000000,19137"})
    void getEpochDay(long timestamp, long expected) {
        assertThat(Utility.getEpochDay(timestamp)).isEqualTo(expected);
    }

    @Test
    void getTopic() {
        ContractLoginfo contractLoginfo = ContractLoginfo.newBuilder()
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 1}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 127}))
                .addTopic(ByteString.copyFrom(new byte[] {-1}))
                .addTopic(ByteString.copyFrom(new byte[] {0}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0}))
                .addTopic(ByteString.copyFrom(new byte[0]))
                .build();
        assertThat(Utility.getTopic(contractLoginfo, 0)).isEqualTo(new byte[] {1});
        assertThat(Utility.getTopic(contractLoginfo, 1)).isEqualTo(new byte[] {127});
        assertThat(Utility.getTopic(contractLoginfo, 2)).isEqualTo(new byte[] {-1});
        assertThat(Utility.getTopic(contractLoginfo, 3)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 4)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 5)).isEmpty();
        assertThat(Utility.getTopic(contractLoginfo, 999)).isNull();
    }

    @Test
    @DisplayName("get TransactionId")
    void getTransactionID() {
        AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
        TransactionID transactionId = Utility.getTransactionId(payerAccountId);
        assertThat(transactionId)
                .isNotEqualTo(TransactionID.getDefaultInstance());

        AccountID testAccountId = transactionId.getAccountID();
        assertAll(
                // row counts
                () -> assertEquals(payerAccountId.getShardNum(), testAccountId.getShardNum())
                , () -> assertEquals(payerAccountId.getRealmNum(), testAccountId.getRealmNum())
                , () -> assertEquals(payerAccountId.getAccountNum(), testAccountId.getAccountNum())
        );
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void instantToTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds).plusNanos(nanos);
        Timestamp test = Utility.instantToTimestamp(instant);
        assertAll(
                () -> assertEquals(instant.getEpochSecond(), test.getSeconds())
                , () -> assertEquals(instant.getNano(), test.getNanos())
        );
    }

    @ParameterizedTest(name = "Convert {0} to snake case")
    @CsvSource({
            ",",
            "\"\",\"\"",
            "Foo,foo",
            "FooBar,foo_bar",
            "foo_bar,foo_bar"
    })
    void toSnakeCase(String input, String output) {
        assertThat(Utility.toSnakeCase(input)).isEqualTo(output);
    }
}
