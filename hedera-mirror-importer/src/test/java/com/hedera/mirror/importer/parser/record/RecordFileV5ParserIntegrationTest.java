package com.hedera.mirror.importer.parser.record;

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

class RecordFileV5ParserIntegrationTest extends AbstractRecordFileParserIntegrationTest {

    RecordFileV5ParserIntegrationTest() {
        super(new RecordFileDescriptor(2, 2, ALL_RECORD_FILE_MAP.get("2021-01-11T22_09_24.063739000Z.rcd")),
                new RecordFileDescriptor(2, 0, ALL_RECORD_FILE_MAP.get("2021-01-11T22_09_34.097416003Z.rcd")));
    }
}
