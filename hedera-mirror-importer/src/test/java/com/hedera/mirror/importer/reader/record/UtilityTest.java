package com.hedera.mirror.importer.reader.record;

/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
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

public class UtilityTest {

    @ParameterizedTest(name = "check byte field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void checkByteField(Byte actual, Byte expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> {
                Utility.checkField(actual, expected, "test field", "testfile");
            });
        } else {
            Utility.checkField(actual, expected, "test field", "testfile");
        }
    }

    @ParameterizedTest(name = "check int field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void checkIntField(Integer actual, Integer expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> {
                Utility.checkField(actual, expected, "test field", "testfile");
            });
        } else {
            Utility.checkField(actual, expected, "test field", "testfile");
        }
    }

    @ParameterizedTest(name = "check int field actual ({0}) expected ({1})")
    @CsvSource({
            "0, 0, false",
            ", , false",
            ", 0, true",
            "0, 1, true"
    })
    void checkLongField(Long actual, Long expected, boolean expectThrown) {
        if (expectThrown) {
            assertThrows(InvalidStreamFileException.class, () -> {
                Utility.checkField(actual, expected, "test field", "testfile");
            });
        } else {
            Utility.checkField(actual, expected, "test field", "testfile");
        }
    }

    @ParameterizedTest(name = "read {0}-byte data from {1}-byte value field")
    @CsvSource({
            "1, 2, 10, false",
            "6, 6, 10, false",
            "6, 10, 10, false",
            "6, 5, 10, true",
            "0, 2, 10, true",
            "-1, 2, 10, true",
            "11, 10, 10, true"
    })
    void readLengthAndBytes(int length, int dataSize, int maxLength, boolean expectThrown) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(length);
            dos.write(new byte[dataSize]);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));

            if (expectThrown) {
                assertThrows(InvalidStreamFileException.class, () -> Utility.readLengthAndBytes(dis, maxLength));
            } else {
                byte[] actual = Utility.readLengthAndBytes(dis, maxLength);

                byte[] expected = new byte[length];
                assertArrayEquals(actual, expected);
            }
        }
    }

    @Test
    void readLengthAndBytesWithTruncatedLengthField() throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(new byte[1]))) {
            assertThrows(IOException.class, () -> Utility.readLengthAndBytes(dis, 10));
        }
    }
}
