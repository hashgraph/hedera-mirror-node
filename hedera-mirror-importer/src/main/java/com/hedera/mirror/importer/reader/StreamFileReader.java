package com.hedera.mirror.importer.reader;

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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.parser.domain.StreamItem;

public interface StreamFileReader<S extends StreamFile, I extends StreamItem> {

    /**
     * Reads a stream file. This method takes ownership of the {@link java.io.InputStream} provided by {@code
     * streamFileData} and will close it when it's done processing the data.
     *
     * @param streamFileData {@link StreamFileData} object for the record file.
     * @param itemConsumer   consumer to handle individual {@link StreamItem} objects.
     * @return {@link StreamFile} object
     */
    S read(StreamFileData streamFileData, Consumer<I> itemConsumer);

    default S read(StreamFileData streamFileData) {
        List<I> items = new ArrayList<>();
        S streamFile = read(streamFileData, items::add);
        streamFile.getItems().addAll(items);
        return streamFile;
    }
}
