package com.hedera.mirror.importer.reader.event;

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

public class EventFileConstants {
    public static final String HASH_ALGORITHM = "SHA-384";
    public static final byte EVENT_TYPE_PREV_HASH = 1; // next 48 bytes are hash384 of previous files
    public static final int  EVENT_PREV_HASH_LENGTH = 48; // sha384 - 48 bytes
    public static final byte EVENT_STREAM_FILE_VERSION_2 = 2;
    public static final byte EVENT_STREAM_FILE_VERSION_3 = 3;
    public static final byte EVENT_STREAM_START_NO_TRANS_WITH_VERSION = 0x5b;
    public static final byte EVENT_STREAM_START_WITH_VERSION = 0x5a;
    public static final byte EVENT_COMM_EVENT_LAST = 0x46;
    public static final byte EVENT_STREAM_EVENT_VERSION_2 = 2; // version 2 event data
    public static final byte EVENT_STREAM_EVENT_VERSION_3 = 3; // version 3 event data
}
