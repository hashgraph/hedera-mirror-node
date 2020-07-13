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

import java.time.Instant;

import com.hedera.mirror.importer.domain.EventFile;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidEventFileException;

public interface EventFileReader {
    /**
     * Read event file. Throws {@link HashMismatchException} on previous file hash mismatch; Throws
     * {@link InvalidEventFileException} on other errors.
     *
     * @param filePath path to event file
     * @param expectedPrevFileHash expected previous event file's hash in current file. Throws {@link HashMismatchException}
     *                             on mismatch
     * @param verifyHashAfter previous file's hash mismatch is ignored if file is from before this time
     * @return {@link EventFile} object
     */
    EventFile read(String filePath, String expectedPrevFileHash, Instant verifyHashAfter);
}
