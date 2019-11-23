package com.hedera.mirror.importer.exception;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
public class InvalidDatasetException extends RuntimeException {
    public InvalidDatasetException() {
        super();
    }

    public InvalidDatasetException(final String s) {
        super(s);
    }

    public InvalidDatasetException(final Throwable t) {
        super(t);
    }

    public InvalidDatasetException(final String s, final Throwable t) {
        super(s, t);
    }
}
