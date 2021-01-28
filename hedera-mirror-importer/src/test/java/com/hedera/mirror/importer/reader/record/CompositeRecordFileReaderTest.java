package com.hedera.mirror.importer.reader.record;

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

public class CompositeRecordFileReaderTest extends RecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        RecordFileReaderImplV1 v1Reader = new RecordFileReaderImplV1();
        RecordFileReaderImplV2 v2Reader = new RecordFileReaderImplV2();
        RecordFileReaderImplV5 v5Reader = new RecordFileReaderImplV5();
        return new CompositeRecordFileReader(v1Reader, v2Reader, v5Reader);
    }

    @Override
    protected boolean filterFile(int version) {
        return true;
    }
}
