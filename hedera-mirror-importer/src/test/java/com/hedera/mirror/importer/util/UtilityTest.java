package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilityTest {

    private static final String ED25519 = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";

    @Test
    void convertSimpleKeyToHexWhenNull() {
        assertThat(Utility.convertSimpleKeyToHex(null)).isNull();
    }

    @Test
    void convertSimpleKeyToHexWhenError() {
        assertThat(Utility.convertSimpleKeyToHex(new byte[] {0, 1, 2})).isNull();
    }

    @Test
    void convertSimpleKeyToHexWhenEd25519() throws Exception {
        var bytes = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519))).build().toByteArray();
        assertThat(Utility.convertSimpleKeyToHex(bytes)).isEqualTo(ED25519);
    }

    @Test
    void convertSimpleKeyToHexWhenSimpleKeyList() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(Utility.convertSimpleKeyToHex(bytes)).isEqualTo(ED25519);
    }

    @Test
    void convertSimpleKeyToHexWhenKeyList() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(Utility.convertSimpleKeyToHex(bytes)).isNull();
    }

    @Test
    void convertSimpleKeyToHexWhenSimpleThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(Utility.convertSimpleKeyToHex(bytes)).isEqualTo(ED25519);
    }

    @Test
    void convertSimpleKeyToHexWhenThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ED25519))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(Utility.convertSimpleKeyToHex(bytes)).isNull();
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

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void convertInstantToNanos(long seconds, int nanos) {
        Long timeNanos = Utility.convertToNanos(seconds, nanos);
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
        assertThrows(ArithmeticException.class, () -> Utility.convertToNanos(seconds, nanos));
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
        assertThat(Utility.convertToNanosMax(seconds, nanos)).isEqualTo(expected);
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
        assertThat(Utility.convertToNanosMax(instant)).isEqualTo(expected);
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

        long timeStampInNanos = Utility.timeStampInNanos(timestamp);
        Instant fromTimeStamp = Instant.ofEpochSecond(0, timeStampInNanos);

        assertAll(
                () -> assertEquals(timestamp.getSeconds(), fromTimeStamp.getEpochSecond())
                , () -> assertEquals(timestamp.getNanos(), fromTimeStamp.getNano())
        );
    }

    @Test
    @DisplayName("converting illegal timestamp to nanos")
    void convertTimestampToNanosIllegalInput() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1568376750538L).setNanos(0).build();
        assertThrows(ArithmeticException.class, () -> {
            Utility.timeStampInNanos(timestamp);
        });
    }
}

