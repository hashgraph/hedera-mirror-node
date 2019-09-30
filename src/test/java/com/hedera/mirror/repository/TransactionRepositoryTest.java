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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionRepositoryTest extends AbstractRepositoryTest {

    @Test
    void insert() {
    	
    	final long chargedTxFee = 100;
    	final long consensusNs = 10;
    	final long validStartNs = 20;
    	final Long recordFileId = insertRecordFile().getId();
    	final Long txEntityId = insertAccountEntity(1, 0, 0, 1).getId();
    	final Long nodeAccountId = insertAccountEntity(2, 0, 0, 3).getId();
    	final Long payerAccountId = insertAccountEntity(3, 0, 0, 4).getId();
    	final Integer transactionTypeId = transactionTypeRepository.findByName("CRYPTOCREATEACCOUNT").get().getId();
    	final long initialBalance = 1000;
    	final byte[] memo = "transaction memo".getBytes();
    	final Integer resultId = transactionResultRepository.findByResult("SUCCESS").get().getId();
    	
    	Transaction transaction = new Transaction();
    	transaction.setChargedTxFee(chargedTxFee);
    	transaction.setConsensusNs(consensusNs);
    	transaction.setEntityId(txEntityId);
    	transaction.setInitialBalance(initialBalance);
    	transaction.setMemo(memo);
    	transaction.setNodeAccountId(nodeAccountId);
    	transaction.setPayerAccountId(payerAccountId);
    	transaction.setRecordFileId(recordFileId);
    	transaction.setResultId(resultId);
    	transaction.setTransactionTypeId(transactionTypeId);
    	transaction.setValidStartNs(validStartNs);
    	
    	transactionRepository.save(transaction);
    	
    	Transaction newTransaction = transactionRepository.findById(consensusNs).get(); 
    	
    	assertAll(
            () -> assertEquals(chargedTxFee, newTransaction.getChargedTxFee())
            ,() -> assertEquals(consensusNs, newTransaction.getConsensusNs())
            ,() -> assertEquals(validStartNs, newTransaction.getValidStartNs())
            ,() -> assertEquals(recordFileId, newTransaction.getRecordFileId())
            ,() -> assertEquals(txEntityId, newTransaction.getEntityId())
            ,() -> assertEquals(nodeAccountId, newTransaction.getNodeAccountId())

            ,() -> assertEquals(payerAccountId, newTransaction.getPayerAccountId())
            ,() -> assertEquals(transactionTypeId, newTransaction.getTransactionTypeId())
            ,() -> assertEquals(initialBalance, newTransaction.getInitialBalance())
            ,() -> assertArrayEquals(memo, newTransaction.getMemo())
            ,() -> assertEquals(resultId, newTransaction.getResultId())
        );
    }
}
