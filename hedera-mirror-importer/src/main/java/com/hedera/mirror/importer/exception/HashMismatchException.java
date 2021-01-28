package com.hedera.mirror.importer.exception;

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

public class HashMismatchException extends ImporterException {

    private static final String MESSAGE = "Hash mismatch for file %s. Expected = %s, Actual = %s";
    private static final long serialVersionUID = -1093315700008851731L;

    public HashMismatchException(String filename, String expectedHash, String actualHash) {
        super(String.format(MESSAGE, filename, expectedHash, actualHash));
    }
}
