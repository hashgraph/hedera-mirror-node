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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@UtilityClass
public class Utility {

    private final String NOT_EQUAL_ERROR_MESSAGE = "Unable to read signature file field %s: Expected %s but got %s";
    private final String NOT_IN_RANGE_ERROR_MESSAGE = "Unable to read signature file field %s: " +
            "Expected value between %d and %d but got %d";
    private final String SECTION_ERROR_MESSAGE_ADDENDUM = "%s %s";

    public void validate(Object expected, Object actual, String fieldName) {
        validate(expected, actual, fieldName, null);
    }

    public void validate(Object expected, Object actual, String fieldName, String sectionName) {
        if (!Objects.equals(expected, actual)) {
            throw new InvalidStreamFileException(
                    String.format(NOT_EQUAL_ERROR_MESSAGE, formatErrorMessageFieldName(fieldName, sectionName),
                            expected, actual));
        }
    }

    public void validateBetween(int minimumExpected, int maximumExpected, int actual, String fieldName,
            String sectionName) {
        if (actual < minimumExpected || actual > maximumExpected) {

            throw new InvalidStreamFileException(
                    String.format(NOT_IN_RANGE_ERROR_MESSAGE, formatErrorMessageFieldName(fieldName, sectionName),
                            minimumExpected, maximumExpected, actual));
        }
    }

    public byte[] readLengthAndBytes(DataInputStream dis, int minLength, int maxLength, boolean hasChecksum,
            String section, String type) {
        try {
            int len = dis.readInt();
            if (minLength == maxLength) {
                validate(minLength, len, type + " length", section);
            } else {
                validateBetween(1, maxLength, len, type + " length", section);
            }

            if (hasChecksum) {
                int checksum = dis.readInt();
                validate(101 - len, checksum, "checksum", section);
            }

            byte[] bytes = dis.readNBytes(len);
            validate(len, bytes.length, "actual " + type + " length", section);
            return bytes;
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    private String formatErrorMessageFieldName(String fieldName, String sectionName) {
        return sectionName != null ? String.format(SECTION_ERROR_MESSAGE_ADDENDUM, sectionName, fieldName) : fieldName;
    }
}
