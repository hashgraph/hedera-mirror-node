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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@UtilityClass
public class ReaderUtility {

    private final String NOT_EQUAL_ERROR_MESSAGE = "Unable to read file %s: Expected %s but got %s";
    private final String NOT_IN_RANGE_ERROR_MESSAGE = "Unable to read file %s: " +
            "Expected value between %d and %d but got %d";
    private final int SIMPLE_SUM = 101;

    public void validate(Object expected, Object actual, String filename, String fieldName) {
        validate(expected, actual, filename, null, fieldName);
    }

    public void validate(Object expected, Object actual, String filename, String sectionName, String fieldName) {
        if (!Objects.equals(expected, actual)) {
            throw new InvalidStreamFileException(
                    String.format(NOT_EQUAL_ERROR_MESSAGE, formatResource(filename, sectionName, fieldName),
                            expected, actual));
        }
    }

    public void validateBetween(int minimumExpected, int maximumExpected, int actual, String filename,
            String sectionName, String fieldName) {
        if (actual < minimumExpected || actual > maximumExpected) {
            throw new InvalidStreamFileException(
                    String.format(NOT_IN_RANGE_ERROR_MESSAGE, formatResource(filename, sectionName, fieldName),
                            minimumExpected, maximumExpected, actual));
        }
    }

    public byte[] readLengthAndBytes(DataInputStream dis, int minLength, int maxLength, boolean hasChecksum,
            String filename, String sectionName, String type) {
        try {
            String typeLength = type + " length";

            int len = dis.readInt();
            if (minLength == maxLength) {
                validate(minLength, len, typeLength, sectionName);
            } else {
                validateBetween(minLength, maxLength, len, filename, sectionName, typeLength);
            }

            if (hasChecksum) {
                int checksum = dis.readInt();
                validate(SIMPLE_SUM - len, checksum, filename, sectionName, "checksum");
            }

            byte[] bytes = dis.readNBytes(len);
            validate(len, bytes.length, filename, sectionName, "actual " + typeLength);
            return bytes;
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    private String formatResource(String filename, String sectionName, String fieldName) {
        List<String> parts = Arrays.asList(filename, "field", sectionName, fieldName).stream()
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
        if (parts.size() == 1) {
            return "";
        }

        return StringUtils.join(parts, ' ');
    }
}
