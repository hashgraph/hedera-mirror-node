package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.converter.InstantConverter;

public class UtilityTest {

    @TempDir
    Path tempDir;

    @Test
    void accountIDToString() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        String parsed = Utility.accountIDToString(accountId);
        assertThat(parsed).isEqualTo("0.0.100");
    }

    @Test
    void ensureDirectory() throws Exception {
        Path directory = tempDir.resolve("created");
        Path file = tempDir.resolve("file");

        Utility.ensureDirectory(directory); // Creates successfully
        Utility.ensureDirectory(directory); // Already exists

        file.toFile().createNewFile();
        assertThatThrownBy(() -> Utility.ensureDirectory(file)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Utility.ensureDirectory(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Loads resource from classpath")
    void getResource() {
        assertThat(Utility.getResource("log4j2.xml")).exists().canRead();
        assertThat(Utility.getResource("log4j2-test.xml")).exists().canRead();
    }

    private Utility getCut() throws SQLException {
        return new Utility();
    }

    @Test
    @DisplayName("protobufKeyToHexIfEd25519OrNull null key")
    public void protobufKeyToHexIfEd25519OrNull_Null() throws InvalidProtocolBufferException, SQLException {
        var result = getCut().protobufKeyToHexIfEd25519OrNull(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("protobufKeyToHexIfEd25519OrNull valid ED25519 key")
    public void protobufKeyToHexIfEd25519OrNull_Valid() throws Exception {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var input = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build();
        var result = getCut().protobufKeyToHexIfEd25519OrNull(input.toByteArray());

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

        var result = getCut().protobufKeyToHexIfEd25519OrNull(input.toByteArray());

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

    @ParameterizedTest(name = "verifyHashChain {5}")
    @CsvSource({
            // @formatter:off
            "'', '', 1970-01-01T00:00:00Z,        2000-01-01T10:00:00.000000Z, true,  passes if both hashes are empty",
            "xx, '', 1970-01-01T00:00:00Z,        2000-01-01T10:00:00.000000Z, true,  passes if hash mismatch and expected hash is empty", // starting stream in middle
            "'', xx, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00.000000Z, false, fails if hash mismatch and actual hash is empty", // bad db state
            "xx, yy, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00.000000Z, false, fails if hash mismatch and hashes are non-empty",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10:00:00.000000Z, true,  passes if hash mismatch but verifyHashAfter is after filename",
            "xx, yy, 2000-01-01T10:00:00.000001Z, 2000-01-01T10:00:00.000000Z, true,  passes if hash mismatch but verifyHashAfter is same as filename",
            "xx, yy, 2000-01-01T09:59:59.999999Z, 2000-01-01T10:00:00.000000Z, false, fails if hash mismatch and verifyHashAfter is before filename",
            "xx, xx, 1970-01-01T00:00:00Z,        2000-01-01T10:00:00.000000Z, true,  passes if hashes are equal"
            // @formatter:on
    })
    public void testVerifyHashChain(String actualPrevFileHash, String expectedPrevFileHash,
                                    @ConvertWith(InstantConverter.class) Instant verifyHashAfter,
                                    @ConvertWith(InstantConverter.class) Instant fileInstant,
                                    Boolean expectedResult, String testName) {
        assertThat(Utility.verifyHashChain(actualPrevFileHash, expectedPrevFileHash, verifyHashAfter, fileInstant))
                .isEqualTo(expectedResult);
    }
}

