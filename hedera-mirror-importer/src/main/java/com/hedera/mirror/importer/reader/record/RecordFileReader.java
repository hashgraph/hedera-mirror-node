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

import java.io.InputStream;
import java.util.function.Consumer;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.RecordItem;

public interface RecordFileReader {

    int MAX_RECORD_LENGTH = 64 * 1024;
    int MAX_TRANSACTION_LENGTH = 64 * 1024;

    /**
     * Reads record file. This method takes ownership of the {@link InputStream} provided by {@code streamFileData} and
     * will close it when it's done processing the data.
     *
     * @param streamFileData {@link StreamFileData} object for the record file.
     * @param itemConsumer consumer to handle individual {@link RecordItem} objects.
     * @return {@link RecordFile} object
     */
    RecordFile read(StreamFileData streamFileData, Consumer<RecordItem> itemConsumer) throws ImporterException;

    default RecordFile read(StreamFileData streamFileData) {
        return read(streamFileData, null);
    }
}
