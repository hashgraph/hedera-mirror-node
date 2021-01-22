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
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

/**
 * A validated data input stream extends {@link DataInputStream} by providing methods to validate several types
 * of value read from the stream with expected value.
 */
public class ValidatedDataInputStream extends DataInputStream {

    private final static String NOT_EQUAL_ERROR_MESSAGE = "Unable to read file %s: Expected %s but got %s";
    private final static String NOT_IN_RANGE_ERROR_MESSAGE = "Unable to read file %s: " +
            "Expected value between %d and %d but got %d";
    private final static int SIMPLE_SUM = 101;

    private final String filename;

    /**
     * Creates a ValidatedDataInputStream that uses the specified underlying InputStream.
     *
     * @param in the specified input stream
     * @param filename the file name of the specified input stream
     */
    public ValidatedDataInputStream(InputStream in, String filename) {
        super(in);
        this.filename = filename;
    }

    public byte readByte(byte expected, String fieldName) throws IOException {
        return readByte(expected, null, fieldName);
    }

    public byte readByte(byte expected, String sectionName, String fieldName) throws IOException {
        byte actual = super.readByte();
        validate(expected, actual, sectionName, fieldName);
        return actual;
    }

    public int readInt(int expected, String fieldName) throws IOException {
        return readInt(expected, null, fieldName);
    }

    public int readInt(int expected, String sectionName, String fieldName) throws IOException {
        int actual = super.readInt();
        validate(expected, actual, sectionName, fieldName);
        return actual;
    }

    public byte[] readLengthAndBytes(int minLength, int maxLength, boolean hasChecksum,
            String type) throws IOException {
        return readLengthAndBytes(minLength, maxLength, hasChecksum, null, type);
    }

    public byte[] readLengthAndBytes(int minLength, int maxLength, boolean hasChecksum, String sectionName,
            String type) throws IOException {
        String typeLength = type + " length";

        int length = super.readInt();
        if (minLength == maxLength) {
            validate(minLength, length, sectionName, typeLength);
        } else {
            validateBetween(minLength, maxLength, length, sectionName, typeLength);
        }

        if (hasChecksum) {
            int checksum = super.readInt();
            validate(SIMPLE_SUM - length, checksum, sectionName, "checksum");
        }

        return readNBytes(length, sectionName, "actual" + typeLength);
    }

    public byte[] readNBytes(int expectedLength, String fieldName) throws IOException {
        return readNBytes(expectedLength, null, fieldName);
    }

    public byte[] readNBytes(int expectedLength, String sectionName, String fieldName) throws IOException {
        byte[] bytes = super.readNBytes(expectedLength);
        validate(expectedLength, bytes.length, sectionName, fieldName);
        return bytes;
    }

    private void validate(Object expected, Object actual, String sectionName, String fieldName) {
        if (!Objects.equals(expected, actual)) {
            throw new InvalidStreamFileException(
                    String.format(NOT_EQUAL_ERROR_MESSAGE, formatResource(sectionName, fieldName),
                            expected, actual));
        }
    }

    private void validateBetween(int minimumExpected, int maximumExpected, int actual, String sectionName,
            String fieldName) {
        if (actual < minimumExpected || actual > maximumExpected) {
            throw new InvalidStreamFileException(
                    String.format(NOT_IN_RANGE_ERROR_MESSAGE, formatResource(sectionName, fieldName),
                            minimumExpected, maximumExpected, actual));
        }
    }

    private String formatResource(String sectionName, String fieldName) {
        List<String> parts = Arrays.asList(filename, "field", sectionName, fieldName).stream()
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
        if (parts.size() == 1) {
            return "";
        }

        return StringUtils.join(parts, ' ');
    }
}
