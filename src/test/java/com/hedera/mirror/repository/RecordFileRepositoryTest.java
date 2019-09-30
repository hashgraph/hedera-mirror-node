package com.hedera.mirror.repository;

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

import com.hedera.mirror.domain.RecordFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordFileRepositoryTest extends AbstractRepositoryTest {

    @Test
    void insert() {
    	
		final long fileId = 1;
		final String fileName = "testfile";
		final String fileHash = "fileHash";
		final long loadStart = 20;
		final long loadEnd = 30;
		final String previousHash = "previousHash";
		
		RecordFile recordFile = new RecordFile();
		recordFile.setId(fileId);
		recordFile.setName(fileName);
		recordFile.setFileHash(fileHash);
		recordFile.setLoadEnd(loadEnd);
		recordFile.setLoadStart(loadStart);
		recordFile.setPreviousHash(previousHash);
		
		recordFile = recordFileRepository.save(recordFile);
    	
    	RecordFile newRecordFile = recordFileRepository.findById(fileId).get(); 
    	
    	assertAll(
            () -> assertEquals(fileId, newRecordFile.getId())
            ,() -> assertEquals(fileName, newRecordFile.getName())
            ,() -> assertEquals(fileHash, newRecordFile.getFileHash())
            ,() -> assertEquals(loadStart, newRecordFile.getLoadStart())
            ,() -> assertEquals(loadEnd, newRecordFile.getLoadEnd())
            ,() -> assertEquals(previousHash, newRecordFile.getPreviousHash())
        );
    }
}
