/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.record;

import jakarta.inject.Named;
import java.io.InputStream;

@Named
public class RecordFileReaderImplV2 extends AbstractPreV5RecordFileReader {

    public RecordFileReaderImplV2() {
        super(2);
    }

    @Override
    protected RecordFileDigest getRecordFileDigest(InputStream is) {
        return new RecordFileDigest(is, false);
    }
}
