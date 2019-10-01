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

import com.hedera.mirror.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

public class TransactionRepositoryTest extends AbstractRepositoryTest {

	Transaction transaction;

    @Test
    @Transactional
    void transactionInsert() {
    	
    	final Long recordFileId = insertRecordFile().getId();
    	final Long txEntityId = insertAccountEntity().getId();
    	final Long nodeAccountId = insertAccountEntity().getId();
    	final Long payerAccountId = insertAccountEntity().getId();
    	final Integer transactionTypeId = transactionTypeRepository.findByName("CRYPTOCREATEACCOUNT").get().getId();
    	final Integer resultId = transactionResultRepository.findByResult("SUCCESS").get().getId();
    	
    	transaction = new Transaction();
    	transaction.setChargedTxFee(100L);
    	transaction.setConsensusNs(10L);
    	transaction.setEntityId(txEntityId);
    	transaction.setInitialBalance(1000L);
    	transaction.setMemo("transaction memo".getBytes());
    	transaction.setNodeAccountId(nodeAccountId);
    	transaction.setPayerAccountId(payerAccountId);
    	transaction.setRecordFileId(recordFileId);
    	transaction.setResultId(resultId);
    	transaction.setTransactionTypeId(transactionTypeId);
    	transaction.setValidStartNs(20L);
    	
    	transaction = transactionRepository.save(transaction);
    	
    	assertThat(transactionRepository.findById(10L).get())
    		.isNotNull()
    		.isEqualTo(transaction);
    }
}
