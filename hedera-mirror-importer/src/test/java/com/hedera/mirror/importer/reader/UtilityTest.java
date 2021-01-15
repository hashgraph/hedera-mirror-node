package com.hedera.mirror.importer.reader;

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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

class UtilityTest {

    @ParameterizedTest(name = "validate byte field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void validateByte(Byte actual, Byte expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> Utility.validate(expected, actual, "test field", "test section"));
        } else {
            Utility.validate(expected, actual, "test field", "test section");
        }
    }

    @ParameterizedTest(name = "validate int field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void validateInt(Integer actual, Integer expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> Utility.validate(expected, actual, "test field", "test section"));
        } else {
            Utility.validate(expected, actual, "test field", "test section");
        }
    }

    @ParameterizedTest(name = "validate int field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void validateLong(Long actual, Long expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> Utility.validate(expected, actual, "test field", "test section"));
        } else {
            Utility.validate(expected, actual, "test field", "test section");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, 10, 1, false",
            "1, 10, 10, false",
            "10, 10, 10, false",
            "1, 10, 0, true",
            "1, 10, 11, true",
            "10, 10, 8, true",
    })
    void validateBetween(int min, int max, int actual, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> Utility.validateBetween(min, max, actual, "test", null));
        } else {
            Utility.validateBetween(min, max, actual, "test", null);
        }
    }

    @ParameterizedTest(name = "read length({0}) data from {1}-byte value field with valid length in [{3}, {4}]")
    @CsvSource({
            "1, 2, 1, 10, false",
            "6, 6, 1, 10, false",
            "6, 10, 1, 10, false",
            "6, 6, 6, 6, false",
            "5, 6, 6, 6, true",
            "6, 5, 1, 10, true",
            "0, 2, 1, 10, true",
            "-1, 2, 1, 10, true",
            "11, 10, 1, 10, true"
    })
    void readLengthAndBytesWithoutChecksum(int length, int dataSize, int minLength, int maxLength,
            boolean expectThrown) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(length);
            dos.write(new byte[dataSize]);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));

            if (expectThrown) {
                assertThrows(InvalidStreamFileException.class,
                        () -> Utility.readLengthAndBytes(dis, minLength, maxLength, false, null, "test"));
            } else {
                byte[] actual = Utility.readLengthAndBytes(dis, minLength, maxLength, false, null, "test");

                byte[] expected = new byte[length];
                assertArrayEquals(actual, expected);
            }
        }
    }

    @ParameterizedTest(name = "read length({0}) data with checksum {1}, expectThrown ? {2}")
    @CsvSource({
            "101, 0, false",
            "101, 100, true"
    })
    void readLengthAdnBytesWithChecksum(int length, int checksum, boolean expectThrown) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(length);
            dos.writeInt(checksum);
            dos.write(new byte[length]);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));

            if (expectThrown) {
                assertThrows(InvalidStreamFileException.class,
                        () -> Utility.readLengthAndBytes(dis, length, length, true, null, "test"));
            } else {
                byte[] actual = Utility.readLengthAndBytes(dis, length, length, true, null, "test");

                byte[] expected = new byte[length];
                assertArrayEquals(actual, expected);
            }
        }
    }

    @Test
    void readLengthAndBytesWithTruncatedLengthField() throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(new byte[1]))) {
            assertThrows(InvalidStreamFileException.class,
                    () -> Utility.readLengthAndBytes(dis, 10, 10, false, null, "test"));
        }
    }
}
