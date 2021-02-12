package com.hedera.mirror.importer.parser;

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

import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.exception.ImporterException;

public interface StreamFileListener<T extends StreamFile> {
    /**
     * Called when starting to process a new stream file.
     */
    void onStart() throws ImporterException;

    void onEnd(T streamFile) throws ImporterException;

    /**
     * Called if an error is encountered during processing of stream file.
     */
    void onError();
}
