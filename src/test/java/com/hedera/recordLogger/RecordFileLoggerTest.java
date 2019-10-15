package com.hedera.recordLogger;



import com.hedera.mirror.domain.RecordFile;

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

import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.recordFileLogger.RecordFileLogger.INIT_RESULT;
import org.junit.jupiter.api.*;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@Sql("classpath:db/scripts/cleanup.sql") // Class manually commits so have to manually cleanup tables
public class RecordFileLoggerTest extends AbstractRecordFileLoggerTest {

	//TODO: The following are not yet saved to the mirror node database
    // transactionBody.getTransactionFee()
    // transactionBody.getTransactionValidDuration()
    // transaction.getSigMap()
	// transactionBody.getNewRealmAdminKey();
	// record.getTransactionHash();

    @BeforeEach
    void before() throws Exception {
		assertTrue(RecordFileLogger.start());
	}

    @AfterEach
    void after() {
    	RecordFileLogger.finish();
    }

    @Test
    void recordFileInitTest() throws Exception {
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		RecordFileLogger.completeFile("", "");
    	final RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
    	assertEquals("TestFile", recordFile.getName());
    }
    
    @Test
    void recordFileDuplicateInitTest() throws Exception {
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		assertEquals(INIT_RESULT.SKIP, RecordFileLogger.initFile("TestFile"));
    }

    @Test
    void recordFileCompleteNoHashesTest() throws Exception {
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		RecordFileLogger.completeFile("", "");
    	final RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
    	assertNull(recordFile.getFileHash());
    	assertNull(recordFile.getPreviousHash());
    }
    
    @Test
    void recordFileCompleteWithHashesTest() throws Exception {
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		RecordFileLogger.completeFile("123", "456");
    	final RecordFile recordFile = recordFileRepository.findById(RecordFileLogger.getFileId()).get();
    	assertEquals("123", recordFile.getFileHash());
    	assertEquals("456", recordFile.getPreviousHash());
    }
    @Test
    void recordFileRollbackTest() throws Exception {
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		RecordFileLogger.rollback();
    	final Optional<RecordFile> recordFile = recordFileRepository.findById(RecordFileLogger.getFileId());
    	assertFalse(recordFile.isPresent());
    	
    }
}
