package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.RecordFile;

public class RecordFileLoggerTest extends AbstractRecordFileLoggerTest {

    @Test
    void initFile() throws Exception {
        assertEquals(RecordFileLogger.INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
        RecordFileLogger.completeFile("", "");
        RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
        assertEquals("TestFile", recordFile.getName());
    }

    @Test
    void initFileDuplicate() throws Exception {
        assertEquals(RecordFileLogger.INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
        assertEquals(RecordFileLogger.INIT_RESULT.SKIP, RecordFileLogger.initFile("TestFile"));
    }

    @Test
    void completeFileNoHashes() throws Exception {
        RecordFileLogger.completeFile("", "");
        RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
        assertNull(recordFile.getFileHash());
        assertNull(recordFile.getPreviousHash());
    }

    @Test
    void completeFileWithHashes() throws Exception {
        RecordFileLogger.completeFile("123", "456");
        RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
        assertEquals("123", recordFile.getFileHash());
        assertEquals("456", recordFile.getPreviousHash());
    }

    @Test
    void rollback() throws Exception {
        RecordFileLogger.rollback();
        Optional<RecordFile> recordFile = recordFileRepository.findById(RecordFileLogger.getFileId());
        assertFalse(recordFile.isPresent());
    }
}
