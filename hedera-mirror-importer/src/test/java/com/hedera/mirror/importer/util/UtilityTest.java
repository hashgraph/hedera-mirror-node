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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.sql.SQLException;
import java.time.Instant;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.hedera.mirror.importer.domain.StreamType;

public class UtilityTest {

    @DisplayName("Get Instant from filename")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            ", 1970-01-01T00:00:00.0Z",
            "1970-01-01T00_00_00.0Z.rcd, 1970-01-01T00:00:00.0Z",
            "2020-06-03T16_45_00.000000001Z_Balances.csv_sig, 2020-06-03T16:45:00.000000001Z",
            "2020-06-03T16_45_00.1Z_Balances.csv, 2020-06-03T16:45:00.1Z",
            "2020-06-03T16:45:00.000000003Z_Balances.csv, 2020-06-03T16:45:00.000000003Z",
            "2262-04-11T23:47:16.854775808Z.evts_sig, 2262-04-11T23:47:16.854775808Z",
    })
    void getInstantFromFilename(String filename, Instant instant) {
        assertThat(Utility.getInstantFromFilename(filename)).isEqualTo(instant);
    }

    @DisplayName("Get timestamp from filename")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            ", 0",
            "1970-01-01T00_00_00.0Z.rcd, 0",
            "2020-06-03T16_45_00.000000001Z_Balances.csv_sig, 1591202700000000001",
            "2020-06-03T16_45_00.1Z_Balances.csv, 1591202700100000000",
            "2020-06-03T16:45:00.000000003Z_Balances.csv, 1591202700000000003",
            "2262-04-11T23:47:16.854775808Z.evts_sig, 9223372036854775807",
    })
    void getTimestampFromFilename(String filename, long timestamp) {
        assertThat(Utility.getTimestampFromFilename(filename)).isEqualTo(timestamp);
    }

    @ParameterizedTest(name = "Get stream filename from instant, stream type {0}")
    @EnumSource(StreamType.class)
    void getStreamFilenameFromInstant(StreamType streamType) {
        final String timestamp = "2020-08-08T09:00:05.123456789Z";
        final String timestampTransformed = "2020-08-08T09_00_05.123456789Z";
        Instant instant = Instant.parse(timestamp);
        String expectedFilename = null;
        switch (streamType) {
            case BALANCE:
                expectedFilename = timestampTransformed + "_Balances.csv";
                break;
            case RECORD:
                expectedFilename = timestampTransformed + ".rcd";
                break;
            case EVENT:
                expectedFilename = timestampTransformed + ".evts";
                break;
        }
        assertThat(Utility.getStreamFilenameFromInstant(streamType, instant)).isEqualTo(expectedFilename);
    }

    @ParameterizedTest(name = "StreamFile instant {0} > instant {1} ? {2}")
    @CsvSource(value = {
            "2020-08-08T09:00:05.123456780Z, 2020-08-08T09:00:05.123456790Z, false",
            "2020-08-08T09:00:05.123456790Z, 2020-08-08T09:00:05.123456790Z, false",
            "2020-08-08T09:00:05.123456790Z, 2020-08-08T09:00:05.123456789Z, true",
            "2020-08-08T09:00:05.123456790Z, , false",
    })
    void isStreamFileAfterInstant(Instant fileInstant, Instant instant, boolean expected) {
        String filename = Utility.getStreamFilenameFromInstant(StreamType.RECORD, fileInstant);
        assertThat(Utility.isStreamFileAfterInstant(filename, instant)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Loads resource from classpath")
    void getResource() {
        assertThat(Utility.getResource("log4j2.xml")).exists().canRead();
        assertThat(Utility.getResource("log4j2-test.xml")).exists().canRead();
    }

    @Test
    @DisplayName("protobufKeyToHexIfEd25519OrNull null key")
    public void protobufKeyToHexIfEd25519OrNull_Null() throws InvalidProtocolBufferException, SQLException {
        var result = Utility.protobufKeyToHexIfEd25519OrNull(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("protobufKeyToHexIfEd25519OrNull valid ED25519 key")
    public void protobufKeyToHexIfEd25519OrNull_Valid() throws Exception {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var input = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build();
        var result = Utility.protobufKeyToHexIfEd25519OrNull(input.toByteArray());

        assertThat(result).isEqualTo(instr);
    }

    @Test
    @DisplayName("protobufKeyToHexIfEd25519OrNull threshold key")
    public void protobufKeyToHexIfEd25519OrNull_ThresholdKey() throws Exception {
        var ks = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(ks))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var input = Key.newBuilder().setThresholdKey(tk).build();

        var result = Utility.protobufKeyToHexIfEd25519OrNull(input.toByteArray());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("get TransactionId")
    public void getTransactionID() {
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
    public void instantToTimestamp(long seconds, int nanos) {
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

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    public void timeStampInNanosTimeStamp(long seconds, int nanos) {
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

