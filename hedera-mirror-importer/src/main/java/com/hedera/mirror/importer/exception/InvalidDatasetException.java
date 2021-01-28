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

/**
 * Invalid dataset such as an account balances dataset.
 */
public class InvalidDatasetException extends ImporterException {

    private static final long serialVersionUID = 3679395824341309905L;

    public InvalidDatasetException(String message) {
        super(message);
    }

    public InvalidDatasetException(Throwable throwable) {
        super(throwable);
    }

    public InvalidDatasetException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
