/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.exception;

/**
 * Invalid ethereum transaction bytes encountered during decode.
 */
@SuppressWarnings("java:S110")
public class InvalidEthereumBytesException extends InvalidDatasetException {

    private static final long serialVersionUID = -3253044226905756499L;

    private static final String DECODE_ERROR_PREFIX_MESSAGE = "Unable to decode %s ethereum transaction bytes, %s";

    public InvalidEthereumBytesException(String ethereumTransactionType, String message) {
        super(String.format(DECODE_ERROR_PREFIX_MESSAGE, ethereumTransactionType, message));
    }
}
