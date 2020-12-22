package com.hedera.mirror.importer.util;

/*-
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

public class FileDelimiter {
    public static final String HASH_ALGORITHM = "SHA-384";

    public static final byte RECORD_TYPE_PREV_HASH = 1; // next 48 bytes are hash384 or previous files
    public static final int RECORD_FORMAT_VERSION = 2;
    public static final byte RECORD_TYPE_RECORD = 2; // next data type is transaction and its record
}
