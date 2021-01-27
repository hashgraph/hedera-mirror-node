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

class RecordFileV2ParserIntegrationTest extends AbstractRecordFileParserIntegrationTest {

    RecordFileV2ParserIntegrationTest() {
        super(new RecordFileDescriptor(93, 8, ALL_RECORD_FILE_MAP.get("2019-08-30T18_10_00.419072Z.rcd")),
                new RecordFileDescriptor(75, 5, ALL_RECORD_FILE_MAP.get("2019-08-30T18_10_05.249678Z.rcd")));
    }
}
