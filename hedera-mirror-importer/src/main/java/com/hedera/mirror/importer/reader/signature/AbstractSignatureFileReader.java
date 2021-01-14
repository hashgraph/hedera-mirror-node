package com.hedera.mirror.importer.reader.signature;

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

import java.util.Objects;

import com.hedera.mirror.importer.exception.SignatureFileParsingException;

public abstract class AbstractSignatureFileReader implements SignatureFileReader {

    protected static final int HASH_DIGEST_TYPE = 0x58ff811b; //denotes SHA-384
    private static final String NOT_EQUAL_ERROR_MESSAGE = "Unable to read signature file %s: Expected %s but got %s";
    private static final String NOT_EQUAL_ERROR_MESSAGE_WITH_SECTION = "Unable to read signature file %s in section " +
            "%s: Expected %s but got %s";
    private static final String NOT_IN_RANGE_ERROR_MESSAGE = "Unable to read signature file %s in section %s: " +
            "Expected value " +
            "between %d and %d but got %d";

    protected void validate(Object expected, Object actual, String fieldName) {
        if (!Objects.equals(expected, actual)) {
            throw new SignatureFileParsingException(String
                    .format(NOT_EQUAL_ERROR_MESSAGE, fieldName, expected, actual));
        }
    }

    protected void validate(Object expected, Object actual, String fieldName, String sectionName) {
        if (!Objects.equals(expected, actual)) {
            throw new SignatureFileParsingException(String
                    .format(NOT_EQUAL_ERROR_MESSAGE_WITH_SECTION, fieldName, sectionName, expected, actual));
        }
    }

    protected void validateBetween(int minimumExpected, int maximumExpected, int actual, String fieldName,
                                   String sectionName) {
        if (Integer.compare(minimumExpected, actual) == 1 || Integer.compare(maximumExpected, actual) == -1) {
            throw new SignatureFileParsingException(String
                    .format(NOT_IN_RANGE_ERROR_MESSAGE, fieldName, sectionName, minimumExpected, maximumExpected,
                            actual));
        }
    }
}
