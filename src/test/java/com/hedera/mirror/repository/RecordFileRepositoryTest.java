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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordFileRepositoryTest extends AbstractRepositoryTest {

	RecordFile recordFile;
    @Test
    @Transactional
    void recordFileInsert() {
    	
		recordFile = new RecordFile();
		recordFile.setName("testfile");
		recordFile.setFileHash("fileHash");
		recordFile.setLoadEnd(20L);
		recordFile.setLoadStart(30L);
		recordFile.setPreviousHash("previousHash");
		
		recordFile = recordFileRepository.save(recordFile);
    	
    	assertThat(recordFileRepository.findById(recordFile.getId()).get())
    		.isNotNull()
			.isEqualTo(recordFile);
    }
}
