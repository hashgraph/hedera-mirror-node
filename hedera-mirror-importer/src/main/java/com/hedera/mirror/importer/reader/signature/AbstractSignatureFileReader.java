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
import lombok.Data;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

public abstract class AbstractSignatureFileReader implements SignatureFileReader {

    protected static final int HASH_DIGEST_TYPE = 0x58ff811b; //denotes SHA-384
    private static final String NOT_EQUAL_ERROR_MESSAGE = "Unable to read signature file %s: Expected %s but got %s";
    private static final String NOT_IN_RANGE_ERROR_MESSAGE = "Unable to read signature file %s: " +
            "Expected value between %d and %d but got %d";
    private static final String SECTION_ERROR_MESSAGE_ADDENDUM = "%s in section %s";

    protected void validate(Object expected, Object actual, String fieldName) {
        validate(expected, actual, fieldName, null);
    }

    protected void validate(Object expected, Object actual, String fieldName, String sectionName) {
        if (!Objects.equals(expected, actual)) {
            String fieldNameMessage = sectionName != null ? String
                    .format(SECTION_ERROR_MESSAGE_ADDENDUM, fieldName, sectionName) : fieldName;
            throw new SignatureFileParsingException(String
                    .format(NOT_EQUAL_ERROR_MESSAGE, fieldNameMessage, expected, actual));
        }
    }

    protected void validateBetween(int minimumExpected, int maximumExpected, int actual, String fieldName,
                                   String sectionName) {
        if (actual < minimumExpected || actual > maximumExpected) {
            String fieldNameMessage = sectionName != null ? String
                    .format(SECTION_ERROR_MESSAGE_ADDENDUM, fieldName, sectionName) : fieldName;
            throw new SignatureFileParsingException(String
                    .format(NOT_IN_RANGE_ERROR_MESSAGE, fieldNameMessage, minimumExpected, maximumExpected,
                            actual));
        }
    }

    @Data
    @RequiredArgsConstructor
    protected class Signature {
        private final byte[] signatureBytes;
        private final FileStreamSignature.SignatureType signatureType;
    }
}
