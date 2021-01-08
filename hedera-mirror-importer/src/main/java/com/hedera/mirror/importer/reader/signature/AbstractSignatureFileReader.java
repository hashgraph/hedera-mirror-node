package com.hedera.mirror.importer.reader.signature;/*
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

import com.hedera.mirror.importer.exception.SignatureFileParsingException;

public abstract class AbstractSignatureFileReader implements SignatureFileReader {
    protected void validateByteValue(byte expected, byte actual, String exceptionMessage) {
        if (expected != actual) {
            throw new SignatureFileParsingException(exceptionMessage + actual);
        }
    }

    protected void validateLongValue(long expected, long actual, String exceptionMessage) {
        if (expected != actual) {
            throw new SignatureFileParsingException(exceptionMessage + actual);
        }
    }

    protected void validateIntValue(int expected, int actual, String exceptionMessage) {
        if (expected != actual) {
            throw new SignatureFileParsingException(exceptionMessage + actual);
        }
    }
}
